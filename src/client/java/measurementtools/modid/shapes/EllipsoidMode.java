package measurementtools.modid.shapes;

public enum EllipsoidMode {
    FIT_TO_BOX,      // Ellipsoid fits inside the selection bounding box
    CENTER_RADIUS    // First block is center, furthest block defines XZ radius
}
