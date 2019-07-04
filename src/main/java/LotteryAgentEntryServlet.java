import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.log4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@WebServlet(urlPatterns = "/play") //Określenie adresu na który mapuje się servlet tzn na którym będzie odpowiadał
public class LotteryAgentEntryServlet extends HttpServlet {

    private static final Logger logger = Logger.getLogger(LotteryAgentEntryServlet.class);
    private static final int DEFAULT_LEVEL = 1;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        List<Integer> randomNumbers = getRandomNumbers(); //generowanie 6 losowych liczb
        Integer level = getLevel(req); //pobranie z obiektu żądania zadanego przez klienta poziomu gry (wygrywamy gdy trafimy 1z6 lub 2z6 lub 3z6 itd)
        LotteryParameters lotteryParameters = new LotteryParameters(randomNumbers, level); //Stworznie obiektu transportowego w którym przekazujemy do lotteryBoss'a wylosowane liczby do sprwdzenia oraz zadany poziom gry
        try {
            String lotteryBossResponse = getLotteryBossResponse(lotteryParameters); //Pobranie odpowiedzi od lotteryBoss
            writeResponseToClient(resp, randomNumbers, lotteryBossResponse); //wpisanie do odpowiedzi wylosowanych liczb oraz odpowiedzi od lotteryBoss
        } catch (WrongResponseException e) {
            logger.info("LotteryBoss wrong response: " + e.getResponseStatusCode(), e);
            writeErrorMessage(resp); //obsługa błędów (status odpowiedzi inny niż 200)
        } catch (IOException e1) {
            logger.info("LotteryBoss communication problem !", e1);
            writeErrorMessage(resp);//obsługa błędów (problem z komunikacja np odpowiedź zajęła wiecej niż określony niżej czas)
        }

    }

    private void writeErrorMessage(HttpServletResponse resp) throws IOException {
        resp
                .getWriter()
                .println("Sorry, we had some troubles, try again later");
    }

    private Integer getLevel(HttpServletRequest req) {
        //pobranie poziomu z parametry w URL czyli adres wywołania moze wygladac np tak
        //http://localhost:8080/lotteryAgent/play?level=3
        String levelString = req.getParameter("level");
        if (levelString == null) {
            return DEFAULT_LEVEL; //jeśli uzytkownik nie podał parametru level to przypisujemy mu wartosc domyslna czyli 1
        }
        try {
            Integer level = Integer.valueOf(levelString);
            if (level < 1 || level > 6) { //sprawdznie czy uzytkownik podał wartość level z żądanego zakresu
                logger.warn("Level out of bound: " + levelString + " returning efault level: " + DEFAULT_LEVEL);
                return DEFAULT_LEVEL;
            }
            return level;
        } catch (NumberFormatException e) {
            //jeśli uzytkownik podał inną niż liczbowa wartość parametry level wtedy łapany jest wyjątek i zwracana wartość domyślna 1
            logger.warn("Exception when parsing level: " + levelString, e);
            return DEFAULT_LEVEL;
        }
    }

    private void writeResponseToClient(HttpServletResponse resp, List<Integer> randomNumbers, String lotteryBossResponseString) throws IOException {
        resp
                .setContentType("text/plain; charset=utf-8"); //ustawienie typu i kodowania odpowiedzi

        //Budowanie tekstu do odpowiedzi
        StringBuilder responseString = new StringBuilder("Twoje liczby to: ")
                .append(randomNumbers)
                .append("\n")
                .append(lotteryBossResponseString);

        resp.getWriter()
                .println(responseString);
    }

    private String getLotteryBossResponse(LotteryParameters lotteryParameters) throws IOException, WrongResponseException {
        String lotteryParametersAsJson = new Gson().toJson(lotteryParameters); // wykorzystanie biblioteki gson do przerobienia obiektu lotteryParameters na jsona
        HttpClient httpClient = HttpClients.createDefault(); //Stworznie HttpClienta z biblioteki ApacheHttpClient którym można wysłać żądanie HTTP

        //Określenie metody (POST) oraz adresu na który wysyłmy żądanie
        //Żądanie wysyłane jest na serwer (według założeni Wildfly) który działa na zmienionym porcie 8080->8082
        HttpPost httpPost = new HttpPost("http://localhost:8082/lotteryBoss/api/results");

        //Ustawiamy w ciele żądania treść którą wysyłam tzn nasz obiekt w formacie json
        httpPost.setEntity(new StringEntity(lotteryParametersAsJson));
        //Ustawiamy nagłówek żądania po to aby poinformować serwer o formacie treści którą do niego wysyłąmy
        httpPost.setHeader(new BasicHeader("content-type", "application/json"));

        //Określenie maksymalnego czasu w milisekundach przez który aplikacja lotteryAgent czeka na odpowiedź od lotteryBoss
        RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(10000).build();
        httpPost.setConfig(requestConfig);

        //Wysłanie żądania i otrzymanie odpowiedzi
        HttpResponse lotteryBossResponse = httpClient.execute(httpPost);

        //Pobranie i sprawdzenie statusu odpowiedzi. Jesli status inny niż 200 to rzucamy wyjątek
        int statusCode = lotteryBossResponse.getStatusLine().getStatusCode();
        if (statusCode == 200) {
            return new BasicResponseHandler()
                    .handleResponse(lotteryBossResponse);
        }
        throw new WrongResponseException(statusCode);

    }

    /**
     * Generowanie 6 liczb losowych z zakresu <1,49>
     * Implementacja wykorzystuje metodę ints klasy Random
     * Przekazane są granice zbioru następnie stwierdzamy ze liczby mają się nie powtarzać (distinct)
     * ma ich byc 6 (limit)
     * oraz mają być posortowane (sorted)
     * mapujemy z IntStream na Stream<Integer> (mapToObject)
     * Tworzymy listę
     */
    private List<Integer> getRandomNumbers() {
        return new Random()
                .ints(1, 50)
                .distinct()
                .limit(6)
                .sorted()
                .mapToObj(Integer::valueOf)
                .collect(Collectors.toList());

    }
}
