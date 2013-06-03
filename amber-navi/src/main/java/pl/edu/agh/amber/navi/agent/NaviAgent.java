package pl.edu.agh.amber.navi.agent;

import pl.edu.agh.amber.navi.drive.NaviDriveHelper;
import pl.edu.agh.amber.navi.dto.NaviPoint;
import pl.edu.agh.amber.navi.eye.NaviEyeHelper;
import pl.edu.agh.amber.navi.track.NaviTrackHelper;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

public class NaviAgent implements Runnable {

    private static final Logger logger = Logger.getLogger(String.valueOf(NaviAgent.class));

    private final List<NaviPoint> route = Collections.synchronizedList(new LinkedList<NaviPoint>());

    private final NaviDriveHelper driveHelper;

    private final NaviTrackHelper trackHelper;

    private final NaviEyeHelper eyeHelper;

    private boolean running = true;

    public NaviAgent(NaviDriveHelper driveHelper, NaviTrackHelper trackHelper, NaviEyeHelper eyeHelper) {
        this.driveHelper = driveHelper;
        this.trackHelper = trackHelper;
        this.eyeHelper = eyeHelper;
    }

    synchronized void stop() {
        running = false;
    }

    synchronized boolean isRunning() {
        return running;
    }

    @SuppressWarnings("unused")
    public void addLastPoint(NaviPoint point) {
        synchronized (route) {
            route.add(point);
            route.notifyAll();
        }
    }

    public void addLastPoints(List<NaviPoint> points) {
        synchronized (route) {
            route.addAll(points);
            route.notifyAll();
        }
    }

    @SuppressWarnings("unused")
    public void addAfterPoint(NaviPoint point, NaviPoint after) {
        synchronized (route) {
            route.add(getIndex(after), point);
            route.notifyAll();
        }
    }

    @SuppressWarnings("unused")
    public void addAfterPoints(List<NaviPoint> points, NaviPoint after) {
        synchronized (route) {
            route.addAll(getIndex(after), points);
            route.notifyAll();
        }
    }

    public static final double MAX_SPEED = 60.0;

    public static final double PROPORTIONAL_FACTOR = 20.0;

    public static final double INTEGRAL_FACTOR = 10000.0;

    public static final double DERIVATIVE_FACTOR = 0.6;

    @Override
    public void run() {
        NaviPoint target, oldTarget, location;

        double proportional, lastProportional = 0.0, derivative, integral = 0.0;
        double speedDiff;

        logger.info("Started");

        try {
            do {
                oldTarget = trackHelper.getLocation();
                if (oldTarget == null) {
                    logger.warning("No location. Sleeping for 1s...");
                    Thread.sleep(1000);
                }
            } while (oldTarget == null);

            while (isRunning()) {
                synchronized (route) {
                    while (route.isEmpty()) {
                        route.wait();
                    }
                    target = route.remove(0);
                    logger.info("Next point: " + target);
                }

                do {
                    location = trackHelper.getLocation();
                    if (location != null) {
                        proportional = getProportional(location, target, oldTarget);
                        derivative = proportional - lastProportional;
                        integral += proportional;

                        speedDiff = proportional / PROPORTIONAL_FACTOR + integral / INTEGRAL_FACTOR
                                + derivative / DERIVATIVE_FACTOR;
                        speedDiff = (speedDiff > MAX_SPEED ? MAX_SPEED
                                : (speedDiff < -MAX_SPEED ? -MAX_SPEED : speedDiff));

                        if (speedDiff < 0) {
                            driveHelper.drive((int) (MAX_SPEED + speedDiff), (int) MAX_SPEED);
                        } else {
                            driveHelper.drive((int) MAX_SPEED, (int) (MAX_SPEED - speedDiff));
                        }

                    } else {
                        logger.warning("No location. Sleeping for 1s...");
                        Thread.sleep(1000);
                    }
                } while (location == null || !location.equals(target));

                oldTarget = target;
            }
        } catch (InterruptedException e) {
            logger.warning("NaviAgent interrupted: " + e.getMessage());
            e.printStackTrace();

        } catch (Exception e) {
            logger.warning("NaviAgent: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int getIndex(NaviPoint point) {
        boolean found = false;
        int index = 0;
        Iterator<NaviPoint> itr = route.iterator();
        while (!found && itr.hasNext()) {
            if (itr.next().equals(point)) {
                found = true;
            } else {
                index += 1;
            }
        }
        return index;
    }

    private static double computeArea(double a1, double a2, double b1, double b2, double c1, double c2) {
        return Math.abs(a1 * b2 + b1 * c2 + c1 * a2 - c1 * b2 - a1 * c2 - b1 * a2) / 2.0;
    }

    private static double computeArea(NaviPoint a, NaviPoint b, NaviPoint c) {
        return computeArea(a.getAbsoluteHorizontal(), a.getAbsoluteVertical(),
                b.getAbsoluteHorizontal(), b.getAbsoluteVertical(),
                c.getAbsoluteHorizontal(), c.getAbsoluteVertical());
    }

    private static double computeLength(NaviPoint a, NaviPoint b) {
        return Math.sqrt(Math.pow(a.getAbsoluteHorizontal() - b.getAbsoluteHorizontal(), 2) +
                Math.pow(a.getAbsoluteVertical() - b.getAbsoluteVertical(), 2));
    }

    private static double getProportional(NaviPoint currentLocation, NaviPoint target, NaviPoint oldTarget) {
        double area, length;
        area = computeArea(currentLocation, oldTarget, target);
        length = computeLength(oldTarget, target);
        return (2 * area) / length;
    }
}
