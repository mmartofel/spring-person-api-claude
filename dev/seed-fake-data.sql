-- Fake data for UI performance testing: 250 persons and 2000 tasks.
-- Seed rows are marked by the e-mail domain 'seed.example.com' and the task title suffix ' #seed'.
-- Cleanup: DELETE FROM task WHERE title LIKE '% #seed'; DELETE FROM person WHERE email LIKE '%@seed.example.com';
BEGIN;
SELECT setseed(0.42);

CREATE TEMP TABLE seed_person ON COMMIT DROP AS
WITH n AS (
    SELECT i,
           (ARRAY['Anna','Piotr','Maria','Jan','Katarzyna','Tomasz','Emma','Liam','Olivia','Noah','Sophia','Lucas',
                  'Mia','Leon','Clara','Hugo','Elena','Marco','Julia','David','Sara','Felix','Nora','Oskar',
                  'Alexander-Maximilian','Zoe'])[1 + floor(random() * 26)::int] AS first_name,
           (ARRAY['Kowalski','Nowak','Smith','Johnson','Müller','Schmidt','Dubois','Martin','Rossi','Bianchi',
                  'García','López','Novák','Svensson','Jensen','O''Brien','Van der Berg','Wiśniewski','Lewandowska',
                  'Papadopoulos','Kim','Nguyen','Silva','Santos','Konstantinopoulou-Montgomery'])[1 + floor(random() * 25)::int] AS last_name,
           random() AS r1, random() AS r2, random() AS r3, random() AS r4
    FROM generate_series(1, 250) AS i
)
SELECT i, first_name, last_name, r1, r2, r3, r4,
       (ARRAY['PL','DE','FR','IT','ES','CZ','SE','DK','IE','NL','GR','US','GB'])[1 + floor(r1 * 13)::int] AS country_code,
       CASE WHEN r2 < 0.3 THEN current_date - (floor(r3 * 60))::int + (floor(r4 * 30))::int END AS available_from
FROM n;

INSERT INTO person (first_name, last_name, email, birth_date, gender, street, postal_code, city, country_code,
                    status, available_from, available_to, version)
SELECT first_name,
       last_name,
       lower(translate(first_name || '.' || replace(replace(last_name, ' ', ''), '''', ''),
                       'üéçíóśńłąężźöäå', 'ueciosnlaezzoaa')) || '.' || i || '@seed.example.com',
       CASE WHEN r3 < 0.85 THEN date '1960-01-01' + floor(r4 * 16000)::int END,
       CASE WHEN r1 < 0.1 THEN NULL WHEN r1 < 0.5 THEN 'MALE' WHEN r1 < 0.95 THEN 'FEMALE' ELSE 'OTHER' END,
       CASE WHEN r2 < 0.8 THEN (ARRAY['Main Street','Long Avenue','Market Square','Station Road','Park Lane',
                                      'Very Long Street Name Used To Check How The UI Truncates Addresses'])[1 + floor(r3 * 6)::int]
                               || ' ' || (1 + floor(r4 * 200))::int END,
       CASE WHEN r2 < 0.8 THEN lpad((floor(r4 * 99999))::int::text, 5, '0') END,
       CASE WHEN r2 < 0.8 THEN (ARRAY['Warsaw','Berlin','Paris','Rome','Madrid','Prague','Stockholm','Copenhagen',
                                      'Dublin','Amsterdam','Athens','New York','London'])[1 + floor(r1 * 13)::int] END,
       CASE WHEN r2 < 0.8 THEN country_code END,
       CASE WHEN r4 < 0.2 THEN 'UNAVAILABLE' ELSE 'AVAILABLE' END,
       available_from,
       CASE WHEN available_from IS NOT NULL AND r1 < 0.7 THEN available_from + (floor(r3 * 90))::int END,
       0
FROM seed_person
ORDER BY i;

INSERT INTO task (title, description, priority, estimate_hours, done, due_date, assignee_id, created_at, updated_at, version)
SELECT left((ARRAY['Prepare','Review','Refactor','Deploy','Migrate','Analyze','Document','Test','Fix','Plan'])[1 + floor(random() * 10)::int]
            || ' ' ||
            (ARRAY['budget forecast','database backup','login page','customer feedback','release notes','API gateway',
                   'team meeting agenda','onboarding checklist','monitoring dashboards','quarterly report for the board of directors and all regional managers'])[1 + floor(random() * 10)::int],
            113) || ' #seed',
       CASE WHEN r < 0.3 THEN NULL
            WHEN r < 0.9 THEN (ARRAY['Check the previous version first and list the differences.',
                                     'Low risk, but needs a second pair of eyes.',
                                     'Coordinate with the owner before starting.'])[1 + floor(random() * 3)::int]
            ELSE repeat('Long description to see how the UI wraps text. ', 20) END,
       (ARRAY['LOW','MEDIUM','MEDIUM','HIGH'])[1 + floor(random() * 4)::int],
       CASE WHEN random() < 0.75 THEN (1 + floor(random() * 80)) / 2.0 END,
       random() < 0.35,
       CASE WHEN random() < 0.8 THEN current_date - 60 + floor(random() * 180)::int END,
       CASE WHEN random() < 0.9 THEN (SELECT id FROM person WHERE email LIKE '%@seed.example.com'
                                      ORDER BY id OFFSET floor(a * 250)::int LIMIT 1) END,
       created,
       least(now(), created + random() * interval '20 days'),
       0
FROM (SELECT i, random() AS r, random() AS a, now() - random() * interval '365 days' AS created
      FROM generate_series(1, 2000) AS i) AS t;

COMMIT;
