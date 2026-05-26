package tv.voidstar.powersink.payout;

import tv.voidstar.powersink.PowerSinkConfig;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public abstract class MoneyCalculator {
    protected static final MathContext CALCULATION_PRECISION = MathContext.DECIMAL128;
    protected static final int RESULT_DIGITS = 4;
    protected static final RoundingMode RESULT_ROUNDING_MODE = RoundingMode.HALF_EVEN;

    private static MoneyCalculator moneyCalculator = null;
    /** 能量→金钱最终倍率，公式结果 × ratio = 实际金钱 */
    protected final BigDecimal ratio;

    protected MoneyCalculator(double ratio) {
        this.ratio = BigDecimal.valueOf(ratio);
    }

    public abstract BigDecimal covertEnergyToMoney(long energy);

    protected static BigDecimal roundResult(BigDecimal val) {
        return val.setScale(RESULT_DIGITS, RESULT_ROUNDING_MODE);
    }

    public static void init() {
        double base = PowerSinkConfig.getRatesBase();
        double multiplier = PowerSinkConfig.getRatesMultiplier();
        double shift = PowerSinkConfig.getRatesShift();
        double ratio = PowerSinkConfig.getRatesRatio();
        String function = PowerSinkConfig.getRatesFunction();
        switch (function == null ? "log" : function) {
            case "root" -> moneyCalculator = new MoneyCalculatorRoot(multiplier, base, shift, ratio);
            default -> moneyCalculator = new MoneyCalculatorLog(multiplier, base, shift, ratio);
        }
    }

    public static MoneyCalculator getMoneyCalculator() {
        if (moneyCalculator == null) init();
        return moneyCalculator;
    }
}
