-- 扩展演示商品库。
-- 使用固定业务 ID 和幂等写法，允许在本地演示环境中重复执行。

INSERT INTO products (
  product_id, merchant_id, name, description, category_id, category_name,
  price, stock, sales, rating, status, tags
)
VALUES
  (
    '10004', 'm10001', '真无线蓝牙耳机',
    '适合日常通勤和网课使用的入耳式真无线耳机，配备充电盒，支持触控操作，轻巧便携。',
    'c_headphone', '耳机', 199.00, 95, 186, 4.50, 'ON_SALE',
    '真无线,蓝牙,通勤,便携'
  ),
  (
    '10005', 'm10001', '开放式运动耳机',
    '适合跑步、骑行和健身场景的开放式蓝牙耳机，佩戴稳固，运动时也能感知周围环境。',
    'c_headphone', '耳机', 259.00, 72, 134, 4.70, 'ON_SALE',
    '开放式,运动,蓝牙,稳固'
  ),
  (
    '10006', 'm10001', '便携机械键盘',
    '适合宿舍、办公室和移动办公的紧凑型机械键盘，支持蓝牙与有线连接，节省桌面空间。',
    'c_accessory', '配件', 369.00, 58, 98, 4.80, 'ON_SALE',
    '机械键盘,办公,蓝牙,紧凑'
  ),
  (
    '10007', 'm10001', '人体工学无线鼠标',
    '面向长时间办公和学习设计的无线鼠标，弧形握持，按键安静，支持多档灵敏度调节。',
    'c_accessory', '配件', 169.00, 110, 245, 4.60, 'ON_SALE',
    '人体工学,无线,静音,办公'
  ),
  (
    '10008', 'm10001', '护眼学习台灯',
    '适合阅读、书写和桌面办公的 LED 台灯，支持亮度调节与多种色温，灯臂角度可调。',
    'c_home', '家居', 239.00, 67, 121, 4.70, 'ON_SALE',
    '护眼,阅读,调光,学习'
  ),
  (
    '10009', 'm10001', '轻量防晒冲锋衣',
    '适合春夏通勤、徒步和短途出行的轻量外套，具备防晒、防泼水和便携收纳特点。',
    'c_clothing', '服装', 329.00, 84, 156, 4.60, 'ON_SALE',
    '防晒,防泼水,户外,轻量'
  )
ON CONFLICT (product_id) DO NOTHING;

INSERT INTO product_images (product_id, image_url, sort_order)
SELECT seed.product_id, seed.image_url, seed.sort_order
FROM (
  VALUES
    (
      '10004',
      'https://images.unsplash.com/photo-1572569511254-d8f925fe2cbb?auto=format&fit=crop&w=900&q=80',
      0
    ),
    (
      '10005',
      'https://images.unsplash.com/photo-1590658268037-6bf12165a8df?auto=format&fit=crop&w=900&q=80',
      0
    ),
    (
      '10006',
      'https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=900&q=80',
      0
    ),
    (
      '10007',
      'https://images.unsplash.com/photo-1527814050087-3793815479db?auto=format&fit=crop&w=900&q=80',
      0
    ),
    (
      '10008',
      'https://images.unsplash.com/photo-1507473885765-e6ed057f782c?auto=format&fit=crop&w=900&q=80',
      0
    ),
    (
      '10009',
      'https://images.unsplash.com/photo-1551488831-00ddcb6c6bd3?auto=format&fit=crop&w=900&q=80',
      0
    )
) AS seed(product_id, image_url, sort_order)
WHERE NOT EXISTS (
  SELECT 1
  FROM product_images existing
  WHERE existing.product_id = seed.product_id
    AND existing.sort_order = seed.sort_order
);
