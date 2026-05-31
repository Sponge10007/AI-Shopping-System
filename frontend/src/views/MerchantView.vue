<script setup lang="ts">
import { PackagePlus, Sparkles, Upload } from 'lucide-vue-next'
import { ref } from 'vue'
import { createMerchantProduct } from '../services/api'

const form = ref({
  name: '蓝牙降噪耳机',
  description: '适合通勤和学习的主动降噪蓝牙耳机',
  price: '299.00',
  stock: 120,
  tags: '蓝牙,降噪,通勤',
  imageUrl: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=80',
})
const result = ref('')

async function submitProduct() {
  const response = await createMerchantProduct({
    name: form.value.name,
    description: form.value.description,
    price: form.value.price,
    stock: form.value.stock,
    tags: form.value.tags.split(',').map((tag) => tag.trim()).filter(Boolean),
    image_urls: [form.value.imageUrl],
  })
  result.value = `${response.product_id} / ${response.status} / ${response.vector_index_status}`
}
</script>

<template>
  <div class="page split-page">
    <section>
      <header class="page-title">
        <p>Merchant</p>
        <h1>商品上架</h1>
      </header>

      <form class="bento-card merchant-form" @submit.prevent="submitProduct">
        <label>
          商品名
          <input v-model="form.name" />
        </label>
        <label>
          价格
          <input v-model="form.price" />
        </label>
        <label>
          库存
          <input v-model.number="form.stock" type="number" min="0" />
        </label>
        <label>
          标签
          <input v-model="form.tags" />
        </label>
        <label class="span-2">
          描述
          <textarea v-model="form.description" rows="4" />
        </label>
        <label class="span-2">
          图片 URL
          <input v-model="form.imageUrl" />
        </label>
        <div class="form-actions span-2">
          <button type="button" class="soft-button">
            <Upload :size="18" />
            <span>图片</span>
          </button>
          <button type="submit" class="black-button">
            <PackagePlus :size="18" />
            <span>上架</span>
          </button>
        </div>
      </form>
    </section>

    <aside class="merchant-preview">
      <section class="bento-card preview-card">
        <img :src="form.imageUrl" :alt="form.name" />
        <div>
          <span class="ai-chip small-chip">AI Index</span>
          <h2>{{ form.name }}</h2>
          <strong>¥{{ form.price }}</strong>
          <p>{{ form.description }}</p>
        </div>
      </section>
      <section class="bento-card ai-note">
        <Sparkles :size="18" />
        <div>
          <h3>索引状态</h3>
          <p>{{ result || '商品保存后将自动提交向量索引任务。' }}</p>
        </div>
      </section>
    </aside>
  </div>
</template>
