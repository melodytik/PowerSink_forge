package tv.voidstar.powersink.payout;

import java.math.BigDecimal;

public class MoneyCalculatorRoot extends MoneyCalculator {
    private final double baseMultiplier;
    private final double rootBase;
    private final double shift;
    private final BigDecimal baseMultiplierBD;
    private final double rootExponent;
    private final BigDecimal shiftBD;

    public MoneyCalculatorRoot(double baseMultiplier, double rootBase, double shift, double ratio) {
        super(ratio);
        this.baseMultiplier = baseMultiplier;
        this.rootBase = rootBase;
        this.shift = shift;

        baseMultiplierBD = BigDecimal.valueOf(baseMultiplier);
        rootExponent = 1.0 / rootBase;
        shiftBD = BigDecimal.valueOf(shift);
    }

    @Override
    public BigDecimal covertEnergyToMoney(long energy) {
        if (energy < 0) throw new IllegalArgumentException("能量不能为负数");

        final BigDecimal tempResult;

        if (energy == 0) {
            tempResult = BigDecimal.ZERO;
        } else {
            tempResult =
                    baseMultiplierBD.multiply(
                            BigDecimal.valueOf(Math.pow(energy, rootExponent)), CALCULATION_PRECISION);
        }

        return roundResult(ratio.multiply(shiftBD.add(tempResult, CALCULATION_PRECISION), CALCULATION_PRECISION));
    }
}