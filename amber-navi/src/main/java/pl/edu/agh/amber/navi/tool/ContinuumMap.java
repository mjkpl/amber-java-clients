package pl.edu.agh.amber.navi.tool;

import java.util.HashMap;
import java.util.TreeSet;

public class ContinuumMap extends HashMap<Double, Double> {

    private final TreeSet<Double> angles = new TreeSet<Double>();

    @Override
    public Double put(Double angle, Double length) {
        angles.add(angle);
        return super.put(angle, length);
    }

    @Override
    public Double get(Object key) {
        Double length = super.get(key);
        if (length == null && key instanceof Double) {
            Double angle = (Double) key;
            Double lower = angles.lower(angle), higher = angles.higher(angle);
            Double left = (lower != null ? super.get(lower) : 0.0);
            Double right = (higher != null ? super.get(higher) : 0.0);
            // paoolo FIXME average with weights
            length = (left + right) / 2;
        }
        return length;
    }

    @Override
    public Double remove(Object key) {
        if (key instanceof Double) {
            angles.remove(key);
        }
        return super.remove(key);
    }
}
