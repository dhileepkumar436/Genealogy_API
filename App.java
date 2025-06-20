package com.genealogy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

import java.sql.*;
import java.util.*;

@SpringBootApplication
@RestController
@RequestMapping("/api")
public class App {

    static final String DB_URL = "jdbc:postgresql://localhost:5433/Geneology.api";
    static final String DB_USER = "postgres";
    static final String DB_PASSWORD = "admin@123";

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @PostMapping("/genealogy")
    public Map<String, Object> getGenealogy(@RequestBody Map<String, String> req) {
        var direction = req.get("direction");
        var sfc = req.get("sfc");
        var site = req.get("site");

        if (direction == null || sfc == null || site == null) {
            return Map.of("error", "Missing required fields: 'direction', 'sfc', and 'site'");
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            return switch (direction.toLowerCase()) {
                case "forward" -> traverseForward(sfc, site, conn, new HashSet<>());
                case "backward" -> traverseBackward(sfc, site, conn, new HashSet<>());
                default -> Map.of("error", "Direction must be either 'forward' or 'backward'");
            };
        } catch (Exception e) {
            return Map.of("error", "Database error: " + e.getMessage());
        }
    }

    private Map<String, Object> traverseBackward(String sfc, String site, Connection conn, Set<String> visited) throws SQLException {
        if (visited.contains(sfc))
            return Map.of("sfc", sfc, "cycle", true, "components", Collections.emptyList());
        visited.add(sfc);

        var root = new LinkedHashMap<String, Object>();
        try (var st = conn.prepareStatement("SELECT site, item FROM sfc WHERE sfc = ? AND site = ?")) {
            st.setString(1, sfc);
            st.setString(2, site);
            try (var rs = st.executeQuery()) {
                if (!rs.next()) return Map.of("error", "SFC not found: " + sfc + " at site " + site);
                root.put("sfc", sfc);
                root.put("item", rs.getString("item"));
                root.put("site", rs.getString("site"));
            }
        }

        List<Map<String, Object>> children = new ArrayList<>();
        var parentBo = "SFCBO:" + site + "," + sfc;

        var query = """
            SELECT sa.assembled_qty, sa."USER", sa.RESOURCE,
                   iad.data_field, iad.data_value,
                   inv.inventory AS inv_sfc, inv.item AS inv_item
            FROM sfc_assy sa
            JOIN inventory_assy_data iad ON iad.sfc_assy_bo = sa.handle
            LEFT JOIN inventory inv ON inv.handle = sa.inventory_bo
            WHERE sa.sfc_bo = ?
        """;

        var temp = new LinkedHashMap<String, Map<String, Object>>();
        try (var ps = conn.prepareStatement(query)) {
            ps.setString(1, parentBo);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    var invSfc = rs.getString("inv_sfc");
                    if (invSfc == null) continue;

                    temp.putIfAbsent(invSfc, new LinkedHashMap<>());
                    var comp = temp.get(invSfc);

                    comp.put("sfc", invSfc);
                    comp.put("item", rs.getString("inv_item"));
                    comp.put("site", site);
                    comp.put("assembly. quantity", rs.getInt("assembled_qty"));
                    comp.put("User", rs.getString("USER"));
                    comp.put("Resources", rs.getString("RESOURCE"));

                    var field = rs.getString("data_field");
                    var val = rs.getString("data_value");
                    switch (field) {
                        case "PHYSICAL_INV" -> comp.put("Physical_INV", val);
                        case "UOM" -> comp.put("UOM", val);
                        case "BATCH_NUMBER" -> comp.put("Batch", val);
                    }
                }
            }
        }

        for (var comp : temp.values()) {
            var childSfc = (String) comp.get("Physical_INV");
            var subComps = (childSfc != null)
                    ? (List<Map<String, Object>>) traverseBackward(childSfc, site, conn, new HashSet<>(visited)).getOrDefault("components", List.of())
                    : List.of();
            comp.put("components", subComps);
            children.add(comp);
        }

        root.put("components", children);
        return root;
    }

    private Map<String, Object> traverseForward(String sfc, String site, Connection conn, Set<String> visited) throws SQLException {
        if (visited.contains(sfc))
            return Map.of("sfc", sfc, "cycle", true, "components", Collections.emptyList());
        visited.add(sfc);

        var root = new LinkedHashMap<String, Object>();
        try (var st = conn.prepareStatement("SELECT site, item FROM sfc WHERE sfc = ? AND site = ?")) {
            st.setString(1, sfc);
            st.setString(2, site);
            try (var rs = st.executeQuery()) {
                if (!rs.next()) return Map.of("error", "SFC not found: " + sfc + " at site " + site);
                root.put("sfc", sfc);
                root.put("item", rs.getString("item"));
                root.put("site", rs.getString("site"));
            }
        }

        var query = """
            SELECT sa.handle, sa.sfc_bo, sa.assembled_qty, sa."USER", sa.RESOURCE
            FROM sfc_assy sa
            JOIN inventory_assy_data iad ON sa.handle = iad.sfc_assy_bo
            WHERE iad.data_field = 'PHYSICAL_INV' AND iad.data_value = ?
        """;

        List<Map<String, Object>> parents = new ArrayList<>();
        try (var ps = conn.prepareStatement(query)) {
            ps.setString(1, sfc);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    var sfcBo = rs.getString("sfc_bo");
                    if (sfcBo == null || !sfcBo.contains(",")) continue;

                    var parentSfc = sfcBo.split(",", 2)[1];

                    var comp = new LinkedHashMap<String, Object>();
                    comp.put("sfc", parentSfc);
                    comp.put("Physical_INV", sfc);
                    comp.put("assembly. quantity", rs.getInt("assembled_qty"));
                    comp.put("User", rs.getString("USER"));
                    comp.put("Resources", rs.getString("RESOURCE"));
                    comp.put("site", site);

                    try (var st2 = conn.prepareStatement("SELECT item FROM sfc WHERE sfc = ? AND site = ?")) {
                        st2.setString(1, parentSfc);
                        st2.setString(2, site);
                        try (var rs2 = st2.executeQuery()) {
                            if (rs2.next()) comp.put("item", rs2.getString("item"));
                        }
                    }

                    var subComps = (List<Map<String, Object>>) traverseForward(parentSfc, site, conn, new HashSet<>(visited)).getOrDefault("components", List.of());
                    comp.put("components", subComps);
                    parents.add(comp);
                }
            }
        }

        root.put("components", parents);
        return root;
    }
}
