# JPA entities reference

## Entity basics
- `@Entity` = this class maps to a table. `@Table(name = "...")` sets the table name.
- `@Id` + `@GeneratedValue(strategy = IDENTITY)` = Postgres generates the id on insert.
- Field names become snake_case columns: `priceCents` -> `price_cents`.
- JPA needs a no-args constructor; make it `protected` so app code uses the real constructor.
- `@Column(nullable = false)` -> `NOT NULL` in the table.

## Choices in PayFlow
| Choice | Why |
|---|---|
| `long priceCents` / `long amountCents` | Money in cents, never `double` (floating point can't store 0.10 exactly) |
| Table `orders`, not `order` | `ORDER` is a reserved SQL word (`ORDER BY`) |
| `@Enumerated(EnumType.STRING)` | Stores `"PENDING"`; the default (`ORDINAL`) stores `0`, which breaks if the enum order changes |
| `@ManyToOne(fetch = LAZY)` on `Order.product` | Many orders -> one product; the product is only loaded from the DB when used |
| No setters for `status` | Status changes are explicit methods written in M5/M6, not free setters |
| `ddl-auto: update` | Hibernate creates/updates tables from entities at startup. OK for dev; real projects use Flyway/Liquibase migrations |

## OrderStatus lifecycle
```
PENDING --(verified webhook)--> PAID --(refund)--> REFUNDED
   \
    --(abandoned / expired)--> CANCELLED
```

## Spring Data repositories
`interface ProductRepository extends JpaRepository<Product, Long>`: no implementation
needed. Spring generates `findAll`, `findById`, `save`, `saveAll`, `count`, `deleteById`, ...
`<Product, Long>` = entity type + id type.

## Seeding demo data
`ProductSeeder implements CommandLineRunner` runs once after startup and inserts products
only when `count() == 0`, so restarts don't create duplicates.

## Check the tables in Postgres
```powershell
docker compose exec db psql -U payflow -d payflow -c "\dt"
docker compose exec db psql -U payflow -d payflow -c "select * from products;"
```
