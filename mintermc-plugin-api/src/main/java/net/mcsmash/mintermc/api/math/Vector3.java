package net.mcsmash.mintermc.api.math;

public class Vector3 {
    private double x, y, z;

    /**
     * Creates a new vector with all coordinates set to 0.
     * 
     * @return a new vector with all coordinates set to 0
     */
    public Vector3() {
        this(0, 0, 0);
    }

    /**
     * Creates a new vector with the given x-coordinate and y and z coordinates set
     * to 0.
     * 
     * @param x the x-coordinate
     * @return a new vector with the given x-coordinate and y and z coordinates set
     *         to 0
     */
    public Vector3(double x) {
        this(x, 0, 0);
    }

    /**
     * Creates a new vector with the given x and y coordinates and z coordinate set
     * to 0.
     * 
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @return a new vector with the given x and y coordinates and z coordinate set
     *         to 0
     */
    public Vector3(double x, double y) {
        this(x, y, 0);
    }

    /**
     * Creates a new vector with the given x, y and z coordinates.
     * 
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param z the z-coordinate
     * @return a new vector with the given x, y and z coordinates
     */
    public Vector3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Gets the x-coordinate of this vector.
     * 
     * @return the x-coordinate of this vector
     */
    public double getX() {
        return x;
    }

    /**
     * Sets the x-coordinate of this vector.
     * 
     * @param x the x-coordinate of this vector
     */
    public void setX(double x) {
        this.x = x;
    }

    /**
     * Gets the y-coordinate of this vector.
     * 
     * @return the y-coordinate of this vector
     */
    public double getY() {
        return y;
    }

    /**
     * Sets the y-coordinate of this vector.
     * 
     * @param y the y-coordinate of this vector
     */
    public void setY(double y) {
        this.y = y;
    }

    /**
     * Gets the z-coordinate of this vector.
     * 
     * @return the z-coordinate of this vector
     */
    public double getZ() {
        return z;
    }

    /**
     * Sets the z-coordinate of this vector.
     * 
     * @param z the z-coordinate of this vector
     */
    public void setZ(double z) {
        this.z = z;
    }

    /**
     * Adds the given vector to this vector.
     * 
     * @param other the vector to add
     * @return this vector
     */
    public Vector3 add(Vector3 other) {
        this.x += other.x;
        this.y += other.y;
        this.z += other.z;
        return this;
    }

    /**
     * Subtracts the given vector from this vector.
     * 
     * @param other the vector to subtract
     * @return this vector
     */
    public Vector3 subtract(Vector3 other) {
        this.x -= other.x;
        this.y -= other.y;
        this.z -= other.z;
        return this;
    }

    /**
     * Multiplies this vector by the given scalar.
     * 
     * @param scalar the scalar to multiply by
     * @return this vector
     */
    public Vector3 multiply(double scalar) {
        this.x *= scalar;
        this.y *= scalar;
        this.z *= scalar;
        return this;
    }

    /**
     * Divides this vector by the given scalar.
     * 
     * @param scalar the scalar to divide by
     * @return this vector
     */
    public Vector3 divide(double scalar) {
        this.x /= scalar;
        this.y /= scalar;
        this.z /= scalar;
        return this;
    }

    /**
     * Calculates the distance between this vector and the given vector.
     * 
     * @param other the vector to calculate the distance to
     * @return the distance between this vector and the given vector
     */
    public double distance(Vector3 other) {
        return Math.sqrt(Math.pow(this.x - other.x, 2) + Math.pow(this.y - other.y, 2) + Math.pow(this.z - other.z, 2));
    }

    /**
     * Calculates the dot product of this vector and the given vector.
     * 
     * @param other the vector to calculate the dot product with
     * @return the dot product of this vector and the given vector
     */
    public double dot(Vector3 other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    /**
     * Calculates the cross product of this vector and the given vector.
     * 
     * @param other the vector to calculate the cross product with
     * @return the cross product of this vector and the given vector
     */
    public Vector3 cross(Vector3 other) {
        return new Vector3(this.y * other.z - this.z * other.y, this.z * other.x - this.x * other.z,
                this.x * other.y - this.y * other.x);
    }

    /**
     * Calculates the magnitude of this vector.
     * 
     * @return the magnitude of this vector
     */
    public double magnitude() {
        return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    /**
     * Normalizes this vector.
     * 
     * @return this vector
     */
    public Vector3 normalize() {
        double magnitude = this.magnitude();
        this.x /= magnitude;
        this.y /= magnitude;
        this.z /= magnitude;
        return this;
    }

    /**
     * Creates a copy of this vector.
     * 
     * @return a copy of this vector
     */
    public Vector3 copy() {
        return new Vector3(this.x, this.y, this.z);
    }

    /**
     * Returns a string representation of this vector.
     * 
     * @return a string representation of this vector
     */
    @Override
    public String toString() {
        return "Vector3{" + "x=" + x + ", y=" + y + ", z=" + z + '}';
    }

    /**
     * Checks if this vector is equal to the given vector.
     * 
     * @param other the vector to compare to
     * @return true if this vector is equal to the given vector, false otherwise
     */
    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (other == null || getClass() != other.getClass())
            return false;
        Vector3 vector3 = (Vector3) other;
        return Double.compare(vector3.x, x) == 0 && Double.compare(vector3.y, y) == 0
                && Double.compare(vector3.z, z) == 0;
    }

    /**
     * Returns the hash code of this vector.
     * 
     * @return the hash code of this vector
     */
    @Override
    public int hashCode() {
        return java.util.Objects.hash(x, y, z);
    }
}
