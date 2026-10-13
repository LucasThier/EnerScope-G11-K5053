-- The original export constant used Cyrillic lookalikes (U+0415 U+0425 U+0420 U+041E R U+0422).
-- Normalize only that exact legacy value to the current ASCII enum constant.
UPDATE node_type_data
SET vertical = 'EXPORT'
WHERE vertical = 'ЕХРОRТ';
