package net.mcsmash.mintermc.api.math;

public class Location {
    private Vector3 position;
    private double yaw, pitch;

    public Location(Vector3 position, double yaw, double pitch) {
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Vector3 getPosition() {
        return position;
    }

    public void setPosition(Vector3 position) {
        this.position = position;
    }

    public double getYaw() {
        return yaw;
    }

    public void setYaw(double yaw) {
        this.yaw = yaw;
    }

    public double getPitch() {
        return pitch;
    }

    public void setPitch(double pitch) {
        this.pitch = pitch;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT,
                "Location{x=%.2f, y=%.2f, z=%.2f, yaw=%.2f, pitch=%.2f}",
                position.getX(), position.getY(), position.getZ(), yaw, pitch);
    }
}
