-- Reference data needed in every environment. No users or credentials here.
-- Same rows as db/migration/postgresql; derived tables use UNION ALL instead of VALUES column aliases.

INSERT INTO countries (name) VALUES ('Egypt');

INSERT INTO provinces (country_id, name)
SELECT c.id, p.name FROM countries c,
     (SELECT 'Cairo' AS name UNION ALL SELECT 'Giza' UNION ALL SELECT 'Alexandria' UNION ALL SELECT 'Qalyubia'
      UNION ALL SELECT 'Dakahlia' UNION ALL SELECT 'Sharqia') AS p
WHERE c.name = 'Egypt';

INSERT INTO cities (province_id, name)
SELECT p.id, x.city FROM provinces p
JOIN (SELECT 'Cairo' AS province, 'Nasr City' AS city UNION ALL SELECT 'Cairo', 'Heliopolis'
      UNION ALL SELECT 'Cairo', 'Maadi' UNION ALL SELECT 'Cairo', 'New Cairo'
      UNION ALL SELECT 'Giza', 'Dokki' UNION ALL SELECT 'Giza', 'Mohandessin'
      UNION ALL SELECT 'Giza', '6th of October' UNION ALL SELECT 'Giza', 'Sheikh Zayed'
      UNION ALL SELECT 'Alexandria', 'Smouha' UNION ALL SELECT 'Alexandria', 'Sidi Gaber'
      UNION ALL SELECT 'Qalyubia', 'Banha' UNION ALL SELECT 'Qalyubia', 'Shubra El Kheima'
      UNION ALL SELECT 'Dakahlia', 'Mansoura' UNION ALL SELECT 'Sharqia', 'Zagazig') AS x
  ON x.province = p.name;

INSERT INTO services (name, description) VALUES
    ('Oil change', 'Engine oil and filter replacement'),
    ('Brake service', 'Brake pads, discs and fluid'),
    ('Tire service', 'Tire change, balancing and alignment'),
    ('Battery replacement', 'Battery test and replacement'),
    ('AC service', 'Air conditioning check and recharge'),
    ('Engine diagnostics', 'Computer diagnostics and inspection'),
    ('Suspension repair', 'Shocks, struts and suspension parts'),
    ('Electrical repair', 'Wiring, lights and electrical faults'),
    ('Car wash', 'Exterior and interior cleaning'),
    ('Body and paint', 'Dent repair and painting');

INSERT INTO car_types (name) VALUES
    ('Toyota'), ('Hyundai'), ('Kia'), ('Nissan'), ('Chevrolet'), ('Renault'), ('Peugeot'), ('Mitsubishi'),
    ('Skoda'), ('Volkswagen'), ('BMW'), ('Mercedes-Benz'), ('MG'), ('Chery'), ('Fiat');

INSERT INTO common_spare_parts (name) VALUES
    ('Oil filter'), ('Air filter'), ('Cabin filter'), ('Brake pads'), ('Brake discs'), ('Spark plugs'),
    ('Battery'), ('Wiper blades'), ('Timing belt'), ('Shock absorbers');

INSERT INTO cancellation_reasons (text, applies_to) VALUES
    ('I found another provider', 'CUSTOMER'),
    ('I no longer need the service', 'CUSTOMER'),
    ('The price is too high', 'CUSTOMER'),
    ('The appointment time does not suit me', 'ANY'),
    ('Parts are not available', 'PROVIDER'),
    ('The workshop is fully booked', 'PROVIDER'),
    ('Other', 'ANY');
