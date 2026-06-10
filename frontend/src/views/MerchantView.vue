<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listMerchantProducts, deleteMerchantProduct } from '../services/api'

const products = ref<any[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try{
    const data = await listMerchantProducts()
    products.value = data.items
    loading.value = false
  }
  catch{
    alert('无法取得数据！')
  }
  
}

onMounted(load)

async function remove(id: string) {
  if (!confirm('确认下架/删除该商品？')) return
  try {
    await deleteMerchantProduct(id)
    products.value = products.value.filter((p) => p.product_id !== id)
  } catch (e) {
    alert('删除失败')
  }
}

</script>

<template>
  <div class="page">
    <div class="max-w-[1200px] mx-auto px-6">
      <header class="page-title" style="margin-bottom:18px;">
        <p style="color:#6b7280">Merchant</p>
        <h1 style="font-size:28px;margin-top:6px;margin-bottom:6px">商家中心</h1>
        <span style="color:#6b7280">商品管理：上架、编辑与补货。</span>
      </header>

      <section class="bento-card" style="padding:18px;">
        <div class="list-actions" style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
          <div>
            <RouterLink to="/merchant/uploads" class="black-button">上架新商品</RouterLink>
          </div>
          <div style="color:#9ca3af">共 {{ products.length }} 个商品</div>
        </div>

        <div style="overflow:auto">
          <table class="product-table" style="width:100%;border-collapse:collapse">
            <thead>
              <tr style="text-align:left;color:#6b7280;border-bottom:1px solid #e5e7eb"><th style="padding:8px">图片</th><th style="padding:8px">名称</th><th style="padding:8px">价格</th><th style="padding:8px">库存</th><th style="padding:8px">标签</th><th style="padding:8px">操作</th></tr>
            </thead>
            <tbody>
              <tr v-if="loading"><td colspan="6" style="padding:16px">加载中...</td></tr>
              <tr v-for="p in products" :key="p.product_id" style="border-bottom:1px solid #f3f4f6">
                <td style="padding:12px"><img :src="p.image_url || (p.image_urls && p.image_urls[0])" style="height:56px;border-radius:8px"/></td>
                <td style="padding:12px">{{ p.name }}</td>
                <td style="padding:12px">¥{{ p.price }}</td>
                <td style="padding:12px">{{ p.stock }}</td>
                <td style="padding:12px">{{ (p.tags || []).join(',') }}</td>
                <td style="padding:12px">
                  <RouterLink :to="{ name: 'product-edit', params: { id: p.product_id } }" class="soft-button" style="margin-right:8px">编辑</RouterLink>
                  <RouterLink :to="{ name: 'product-restock', params: { id: p.product_id } }" class="soft-button" style="margin-right:8px">补货</RouterLink>
                  <button class="soft-button" @click="() => remove(p.product_id)">下架</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </div>
</template>
