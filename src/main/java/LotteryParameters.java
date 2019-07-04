import java.util.List;

/**
 * Obiekt transportowy
 */
public class LotteryParameters {

    private  List<Integer> userNumbers;
    private Integer level;

    public LotteryParameters() {
    }

    LotteryParameters(List<Integer> userNumbers, Integer level) {
        this.userNumbers = userNumbers;
        this.level = level;
    }

    public List<Integer> getUserNumbers() {
        return userNumbers;
    }

    public Integer getLevel() {
        return level;
    }
}
