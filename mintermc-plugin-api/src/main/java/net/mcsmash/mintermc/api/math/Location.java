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
}
