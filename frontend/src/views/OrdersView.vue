<script setup lang="ts">
import { CreditCard, PackageCheck, Truck } from 'lucide-vue-next'

const orders = [
  {
    id: 'o10001',
    status: 'CREATED',
    amount: '598.00',
    product: '蓝牙降噪耳机 x2',
    receiver: '张三 / 浙江省杭州市',
    progress: '等待支付',
  },
  {
    id: 'o10002',
    status: 'SHIPPING',
    amount: '129.00',
    product: '智能保温杯 x1',
    receiver: '张三 / 浙江省杭州市',
    progress: '运输中',
  },
]
</script>

<template>
  <div class="page narrow-page">
    <header class="page-title">
      <p>Orders</p>
      <h1>我的订单</h1>
    </header>

    <section class="bento-card order-summary">
      <div>
        <PackageCheck :size="28" />
        <h2>近期购物状态</h2>
        <p>AI 已根据物流、售后和库存状态整理你的待处理订单。</p>
      </div>
      <RouterLink to="/" class="black-button">继续购物</RouterLink>
    </section>

    <section class="order-stack">
      <article v-for="order in orders" :key="order.id" class="apple-row-card">
        <div class="row-icon">
          <Truck :size="22" />
        </div>
        <div class="row-main">
          <strong>{{ order.product }}</strong>
          <span>{{ order.receiver }}</span>
        </div>
        <div class="row-meta">
          <span>{{ order.progress }}</span>
          <strong>¥{{ order.amount }}</strong>
        </div>
        <button type="button" class="circle-button" title="支付订单">
          <CreditCard :size="18" />
        </button>
      </article>
    </section>
  </div>
</template>
