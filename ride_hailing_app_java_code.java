import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

// ==========================================
// Custom Exception
// ==========================================
class NoDriverAvailableException extends Exception {
    public NoDriverAvailableException(String message) {
        super(message);
    }
}

// ==========================================
// Interface & Implementations (Fare Strategy)
// ==========================================
interface FareCalculator {
    double calculateFare(double distanceKm, String vehicleType);
}

class NormalFare implements FareCalculator {
    @Override
    public double calculateFare(double distanceKm, String vehicleType) {
        double baseRate = switch (vehicleType.toLowerCase()) {
            case "mini" -> 12.0;
            case "sedan" -> 18.0;
            default -> 10.0; // Auto
        };
        return distanceKm * baseRate;
    }
}

class SurgeFare implements FareCalculator {
    private double surgeMultiplier;

    public SurgeFare(double surgeMultiplier) {
        this.surgeMultiplier = surgeMultiplier;
    }

    @Override
    public double calculateFare(double distanceKm, String vehicleType) {
        double baseFare = new NormalFare().calculateFare(distanceKm, vehicleType);
        return baseFare * surgeMultiplier;
    }
}

// ==========================================
// Domain Classes
// ==========================================
class Location {
    private double x;
    private double y;

    public Location(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double distanceTo(Location other) {
        return Math.sqrt(Math.pow(this.x - other.x, 2) + Math.pow(this.y - other.y, 2));
    }
}

abstract class User {
    protected String id;
    protected String name;
    protected String phone;

    public User(String id, String name, String phone) {
        this.id = id;
        this.name = name;
        this.phone = phone;
    }

    public String getName() { return name; }
    public String getId() { return id; }
}

class Rider extends User {
    public Rider(String id, String name, String phone) {
        super(id, name, phone);
    }
}

class Driver extends User {
    private Location currentLocation;
    private boolean isAvailable;
    private double earnings;
    private String vehicleType;

    public Driver(String id, String name, String phone, Location location, String vehicleType) {
        super(id, name, phone);
        this.currentLocation = location;
        this.isAvailable = true;
        this.earnings = 0.0;
        this.vehicleType = vehicleType;
    }

    public synchronized boolean isAvailable() { return isAvailable; }
    public synchronized void setAvailable(boolean available) { this.isAvailable = available; }
    public Location getCurrentLocation() { return currentLocation; }
    public String getVehicleType() { return vehicleType; }
    public synchronized double getEarnings() { return earnings; }
    public synchronized void addEarnings(double amount) { this.earnings += amount; }
}

class Ride {
    private String rideId;
    private Rider rider;
    private Driver driver;
    private double distance;
    private double fare;

    public Ride(String rideId, Rider rider, Driver driver, double distance, double fare) {
        this.rideId = rideId;
        this.rider = rider;
        this.driver = driver;
        this.distance = distance;
        this.fare = fare;
    }

    public String toCSV() {
        return String.format("%s,%s,%s,%.2f,%.2f", rideId, rider.getName(), driver.getName(), distance, fare);
    }

    public String getRideId() { return rideId; }
    public Rider getRider() { return rider; }
    public Driver getDriver() { return driver; }
    public double getDistance() { return distance; }
    public double getFare() { return fare; }
}

// ==========================================
// Central System Service (Thread Safe)
// ==========================================
class RideHailingService {
    private List<Driver> drivers = new ArrayList<>();
    private List<Ride> rideHistory = new ArrayList<>();
    private static final String FILE_NAME = "ride_history.txt";

    public RideHailingService() {
        // Sample Drivers
        drivers.add(new Driver("D1", "Rahul Sharma", "9876543210", new Location(2, 3), "Auto"));
        drivers.add(new Driver("D2", "Amit Patel", "9876543211", new Location(5, 5), "Mini"));
        drivers.add(new Driver("D3", "Suresh Kumar", "9876543212", new Location(10, 12), "Sedan"));
        drivers.add(new Driver("D4", "Priya Singh", "9876543213", new Location(1, 1), "Mini"));
    }

    public synchronized Driver matchAndBookDriver(Rider rider, Location pickupLoc, String vehicleType, double distance) throws NoDriverAvailableException {
        Driver nearestDriver = null;
        double minDistance = Double.MAX_VALUE;

        for (Driver driver : drivers) {
            if (driver.isAvailable() && driver.getVehicleType().equalsIgnoreCase(vehicleType)) {
                double dist = driver.getCurrentLocation().distanceTo(pickupLoc);
                if (dist < minDistance) {
                    minDistance = dist;
                    nearestDriver = driver;
                }
            }
        }

        if (nearestDriver == null) {
            throw new NoDriverAvailableException("No available " + vehicleType + " drivers nearby!");
        }

        nearestDriver.setAvailable(false); // Assign driver

        FareCalculator fareCalc = (distance > 15) ? new SurgeFare(1.5) : new NormalFare();
        double fare = fareCalc.calculateFare(distance, vehicleType);

        nearestDriver.addEarnings(fare);
        Ride ride = new Ride(UUID.randomUUID().toString().substring(0, 8), rider, nearestDriver, distance, fare);
        rideHistory.add(ride);
        saveRideToFile(ride);

        return nearestDriver;
    }

    private void saveRideToFile(Ride ride) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME, true))) {
            writer.write(ride.toCSV());
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<Ride> getRideHistory() {
        return rideHistory;
    }

    public List<Driver> getDrivers() {
        return drivers;
    }
}

// ==========================================
// Swing GUI Interface
// ==========================================
public class Main extends JFrame {
    private RideHailingService service = new RideHailingService();
    private DefaultTableModel tableModel;

    private JTextField txtRiderName, txtDistance, txtPickupX, txtPickupY;
    private JComboBox<String> cbVehicle;

    public Main() {
        setTitle("Ride-Hailing App Simulator (Ola / Uber Style)");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Top Form Panel
        JPanel inputPanel = new JPanel(new GridLayout(6, 2, 5, 5));
        inputPanel.setBorder(BorderFactory.createTitledBorder("Request a Ride"));

        inputPanel.add(new JLabel(" Rider Name:"));
        txtRiderName = new JTextField("John Doe");
        inputPanel.add(txtRiderName);

        inputPanel.add(new JLabel(" Pickup Location X:"));
        txtPickupX = new JTextField("2");
        inputPanel.add(txtPickupX);

        inputPanel.add(new JLabel(" Pickup Location Y:"));
        txtPickupY = new JTextField("3");
        inputPanel.add(txtPickupY);

        inputPanel.add(new JLabel(" Trip Distance (km):"));
        txtDistance = new JTextField("10");
        inputPanel.add(txtDistance);

        inputPanel.add(new JLabel(" Vehicle Type:"));
        cbVehicle = new JComboBox<>(new String[]{"Auto", "Mini", "Sedan"});
        inputPanel.add(cbVehicle);

        JButton btnBook = new JButton("Book Ride");
        inputPanel.add(btnBook);

        add(inputPanel, BorderLayout.NORTH);

        // Table Panel for History
        String[] columns = {"Ride ID", "Rider", "Driver", "Distance (km)", "Fare ($)"};
        tableModel = new DefaultTableModel(columns, 0);
        JTable table = new JTable(tableModel);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // Action Listener
        btnBook.addActionListener(e -> bookRide());
    }

    private void bookRide() {
        String riderName = txtRiderName.getText();
        String vehicle = (String) cbVehicle.getSelectedItem();

        try {
            double x = Double.parseDouble(txtPickupX.getText());
            double y = Double.parseDouble(txtPickupY.getText());
            double dist = Double.parseDouble(txtDistance.getText());

            Rider rider = new Rider("R1", riderName, "1234567890");
            Location loc = new Location(x, y);

            // Multithreaded request simulation
            new Thread(() -> {
                try {
                    Driver assignedDriver = service.matchAndBookDriver(rider, loc, vehicle, dist);
                    List<Ride> history = service.getRideHistory();
                    Ride lastRide = history.get(history.size() - 1);

                    SwingUtilities.invokeLater(() -> {
                        tableModel.addRow(new Object[]{
                                lastRide.getRideId(),
                                lastRide.getRider().getName(),
                                lastRide.getDriver().getName(),
                                lastRide.getDistance(),
                                String.format("%.2f", lastRide.getFare())
                        });
                        JOptionPane.showMessageDialog(this, "Driver Assigned: " + assignedDriver.getName());
                    });
                } catch (NoDriverAvailableException ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, ex.getMessage(), "Booking Error", JOptionPane.ERROR_MESSAGE));
                }
            }).start();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter valid numeric values for coordinates and distance.");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new Main().setVisible(true);
        });
    }
}