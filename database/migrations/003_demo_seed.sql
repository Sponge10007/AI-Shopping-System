INSERT INTO users (user_id, username, phone, password_hash, role, status)
VALUES
  ('u10001', 'alice', '13800000000', 'dev-password-hash', 'CUSTOMER', 'ACTIVE'),
  ('m10001', 'merchant', '13800000001', 'dev-password-hash', 'MERCHANT', 'ACTIVE'),
  ('admin10001', 'admin', '13800000002', 'dev-password-hash', 'ADMIN', 'ACTIVE')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO user_profiles (user_id, nickname, avatar_url)
VALUES
  ('u10001', 'Alice', 'https://example.com/avatar.png'),
  ('m10001', '示例商家', null),
  ('admin10001', '平台管理员', null)
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO products (
  product_id, merchant_id, name, description, category_id, category_name,
  price, stock, sales, rating, status, tags
)
VALUES
  (
    '10001', 'm10001', '蓝牙降噪耳机',
    '适合通勤和学习的主动降噪蓝牙耳机，黑色头戴式，支持长续航。',
    'c_headphone', '耳机', 299.00, 120, 320, 4.80, 'ON_SALE',
    '蓝牙,降噪,通勤'
  ),
  (
    '10002', 'm10001', '智能保温杯',
    '适合办公和通勤的智能保温杯，支持温度显示，便携防漏。',
    'c_home', '家居', 129.00, 80, 210, 4.60, 'ON_SALE',
    '办公,保温,便携'
  ),
  (
    '10003', 'm10001', '轻量运动背包',
    '适合短途出行和健身的轻量运动背包，分区收纳，防泼水。',
    'c_outdoor', '户外', 189.00, 64, 148, 4.70, 'ON_SALE',
    '运动,收纳,轻量'
  )
ON CONFLICT (product_id) DO NOTHING;

INSERT INTO product_images (product_id, image_url, sort_order)
VALUES
  ('10001', 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=80', 0),
  ('10002', 'https://images.unsplash.com/photo-1602143407151-7111542de6e8?auto=format&fit=crop&w=900&q=80', 0),
  ('10003', 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=900&q=80', 0)
ON CONFLICT DO NOTHING;
