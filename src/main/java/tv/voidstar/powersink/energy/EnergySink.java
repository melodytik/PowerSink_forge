package tv.voidstar.powersink.energy;

import org.bukkit.Location;
import tv.voidstar.powersink.energy.compat.EnergyCapability;
import tv.voidstar.powersink.energy.compat.EnergyType;

import java.util.UUID;

public class EnergySink extends EnergyNode {

    public EnergySink(Location location, UUID playerOwner, EnergyType energyType) {
        super(location, playerOwner, energyType);
    }

    public EnergySink() {}

    @Override
    public void handleEnergyTick() {
        EnergyCapability.withdrawPaymentAndAddEnergy(this);
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.SINK;
    }
}
