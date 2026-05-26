package tv.voidstar.powersink.energy;

import org.bukkit.Location;
import tv.voidstar.powersink.energy.compat.EnergyCapability;
import tv.voidstar.powersink.energy.compat.EnergyType;

import java.util.UUID;

public class EnergySource extends EnergyNode {

    public EnergySource(Location location, UUID playerOwner, EnergyType energyType) {
        super(location, playerOwner, energyType);
    }

    public EnergySource() {}

    @Override
    public void handleEnergyTick() {
        EnergyCapability.removeEnergyAndPay(this);
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.SOURCE;
    }
}
