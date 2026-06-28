-- Standardize business IDs used by the current order service.
-- Existing installations should remove or migrate incompatible legacy IDs
-- before applying these constraints.

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'orders_order_id_format'
  ) THEN
    ALTER TABLE orders
      ADD CONSTRAINT orders_order_id_format
      CHECK (order_id ~ '^o[0-9]+$');
  END IF;
END
$$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'payments_payment_id_format'
  ) THEN
    ALTER TABLE payments
      ADD CONSTRAINT payments_payment_id_format
      CHECK (payment_id ~ '^pay[0-9]+$');
  END IF;
END
$$;
