<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { restockProduct, getMerchantProduct } from '../services/api'

const route = useRoute()
const router = useRouter()
const id = String(route.params.id || '')

const product = ref<any>(null)
const amount = ref<number>(0)
const message = ref('')

onMounted(async () => {
  if (!id) return
  product.value = await getMerchantProduct(id)
})

async function submitRestock() {
  if (!Number.isInteger(amount.value) || amount.value <= 0) {
    message.value = '补货数量必须为正整数'
    return
  }
  try {
    const data = await restockProduct(id, amount.value)
    product.value.stock = data.stock
    message.value = '补货成功'
  }
  catch{
    alert("补货失败！")
  }
  
}
</script>

<template>
  <div class="page">
    <div class="max-w-[700px] mx-auto px-6">
      <header class="page-title">
        <p style="color:#6b7280">Merchant</p>
        <h1 style="font-size:22px;margin-top:6px">商品补货</h1>
      </header>

      <section class="bento-card" style="padding:18px">
        <div v-if="product">
          <h2 style="margin-bottom:6px">{{ product.name }}</h2>
          <p style="color:#6b7280;margin-bottom:12px">当前库存：{{ product.stock }}</p>
        </div>

        <label style="display:block;margin-bottom:12px">
          补货数量
          <input v-model.number="amount" type="number" min="1" style="margin-left:8px;padding:6px;border-radius:6px;border:1px solid #e5e7eb" />
        </label>

        <div class="form-actions">
          <button class="black-button" @click="submitRestock" style="padding:10px 16px">提交</button>
        </div>

        <div v-if="message" style="margin-top:12px;color:#16a34a">{{ message }}</div>
      </section>
    </div>
  </div>
</template>
