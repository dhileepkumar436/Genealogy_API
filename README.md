# 🌳 Genealogy API - Java Spring Boot Backend

A powerful backend API to trace the **assembly genealogy** (parent-child relationships) of components either **forward** or **backward**, starting from a given SFC (Shop Floor Control) ID.

---

## 🔧 Features

- 🔍 Trace genealogy **forward** (downstream components) or **backward** (upstream sources)
- 📦 Deep recursive resolution of multi-level assembly relationships
- 🧮 Quantity, batch, and inventory tracking
- ⚙️ RESTful API with structured JSON output
- 🗃️ PostgreSQL database integration
- 🚀 Lightweight and fast recursive querying

---

## 🛠️ Tech Stack

- **Java 17**
- **Spring Boot**
- **Spring Web**
- **PostgreSQL**
- **JDBC (raw SQL queries)**
- **Maven**


---

## 🗃️ Database Schema

The following PostgreSQL tables are used:

### 🔗 `sfc`
Stores SFC details.

| Field     | Type    | Description         |
|-----------|---------|---------------------|
| sfc       | String  | SFC ID              |
| inventory | String  | Inventory ID        |

### 🔩 `inventory`
Master list of inventory items.

| Field         | Type    | Description             |
|---------------|---------|-------------------------|
| inventory_id  | String  | Inventory ID            |
| description   | String  | Description             |

### 🧬 `sfc_assy`
Maps SFC to its assembly tree.

| Field        | Type    | Description         |
|--------------|---------|---------------------|
| parent_sfc   | String  | Parent SFC          |
| child_sfc    | String  | Child SFC           |

### 🧾 `inventory_assy_data`
Maps inventory-based assembly components.

| Field          | Type    | Description             |
|----------------|---------|-------------------------|
| parent_inv     | String  | Parent inventory ID     |
| child_inv      | String  | Child inventory ID      |
| quantity       | Integer | Quantity used           |

---

## 🌐 API Endpoint

### 📥 Request

```http
POST /api/genealogy
Content-Type: application/json

{
  "sfc": "SFC10",
  "direction": "backward" // or "forward"
}


