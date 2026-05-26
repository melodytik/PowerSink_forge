package tv.voidstar.powersink.payout;

import java.math.BigDecimal;

public class MoneyCalculatorLog extends MoneyCalculator {
    private final double baseMultiplier;
    private final double logBase;
    private final double shift;

    private final BigDecimal baseMultiplierBD;

    private final BigDecimal baseMultiplierHelper;

    /**
     * = 1 / ln(logBase)<br>
     * <br>
     * 这是一个用于计算金额对数的常量。<br>
     * 由于我们只有 BigInteger 的 log2，公式为 <code>log_b(x) = ln(x) / ln(b)
     * </code>。所以我们可以预先计算 <code>ln(b)</code>。而且由于乘法比除法更快，
     * 我们也计算了倒数，这样后续只需用它进行乘法运算。
     */
    private final BigDecimal logHelper;

    private final BigDecimal shiftBD;

    private final BigDecimal logBaseBD;

    public MoneyCalculatorLog(double baseMultiplier, double logBase, double shift, double ratio) {
        super(ratio);
        this.baseMultiplier = baseMultiplier;
        this.logBase = logBase;
        this.shift = shift;

        baseMultiplierBD = BigDecimal.valueOf(baseMultiplier);
        baseMultiplierHelper = BigDecimal.ONE.divide(baseMultiplierBD);
        logHelper = BigDecimal.ONE.divide(BigDecimal.valueOf(Math.log(logBase)), CALCULATION_PRECISION);
        shiftBD = BigDecimal.valueOf(shift);
        logBaseBD = BigDecimal.valueOf(logBase);
    }

    @Override
    public BigDecimal covertEnergyToMoney(long energy) {
        if (energy < 0) throw new IllegalArgumentException("能量不能为负数");

        final BigDecimal tempResult;

        if (energy == 0) {
            tempResult = BigDecimal.ZERO;
        } else {
            // baseMultiplier * ((logHelper * ln(money)) + 1)
            // 也就是
            // baseMultiplier * (log_logBase(money) + 1)
            tempResult =
                    baseMultiplierBD.multiply(
                            logHelper
                                    .multiply(BigDecimal.valueOf(Math.log(energy)), CALCULATION_PRECISION)
                                    .add(BigDecimal.ONE, CALCULATION_PRECISION),
                            CALCULATION_PRECISION);
        }

        return roundResult(ratio.multiply(shiftBD.add(tempResult, CALCULATION_PRECISION), CALCULATION_PRECISION));
    }
}