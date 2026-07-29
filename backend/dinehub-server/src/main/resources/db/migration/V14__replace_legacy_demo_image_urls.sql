-- Earlier demo data referenced image files that were never bundled with the project.
-- Keep existing user-uploaded URLs untouched and replace only the known legacy paths.
UPDATE dish
SET image = CASE MOD(id, 4)
    WHEN 0 THEN '/h5/demo-food/charcoal-fish.png'
    WHEN 1 THEN '/h5/demo-food/spicy-bullfrog.png'
    WHEN 2 THEN '/h5/demo-food/garlic-greens.png'
    ELSE '/h5/demo-food/two-person-set.png'
END
WHERE image LIKE '/images/dish/%';

UPDATE setmeal
SET image = '/h5/demo-food/two-person-set.png'
WHERE image LIKE '/images/setmeal/%';
