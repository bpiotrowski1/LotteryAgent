public class WrongResponseException extends Throwable {

    private int responseStatusCode;

    public WrongResponseException(int responseStatusCode) {
        this.responseStatusCode = responseStatusCode;
    }

    public int getResponseStatusCode() {
        return responseStatusCode;
    }
}
