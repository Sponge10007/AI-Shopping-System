<script setup lang="ts">
import { PackagePlus, Sparkles, Upload } from 'lucide-vue-next'
import { ref, watch, watchEffect } from 'vue'
import { createMerchantProduct, uploadProductImage } from '../services/api'

const form = ref({
  name: '蓝牙降噪耳机',
  description: '适合通勤和学习的主动降噪蓝牙耳机',
  price: '299.00',
  stock: 120,
  tags: '蓝牙,降噪,通勤',
  imageUrl: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=80',
})
const result = ref('')
const errors = ref<string[]>([])
const uploading = ref(false)


async function submitProduct() {
  errors.value = []
  if (!form.value.name?.trim()) 
  {
    errors.value.push('商品名不能为空')
  }

  const priceNum = Number(form.value.price)
  if (Number.isNaN(priceNum) || priceNum <= 0) {
    errors.value.push('价格必须为大于 0 的数字')
  }

  if (!Number.isInteger(form.value.stock) || form.value.stock < 0) {
    errors.value.push('库存必须为非负整数')
  }

  if (!form.value.imageUrl) {
    errors.value.push('请上传商品图片')
  }

  const isImage = await isImageUrl(form.value.imageUrl);
  if(!isImage){
    errors.value.push('无法获得图片,请切换url')
  }

  if (errors.value.length) return

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

function isImageUrl(url: string): Promise<boolean> {
  if(url.startsWith("https://example.com/uploads/products/")){
    return Promise.resolve(true)
  }
  return new Promise((resolve) => {
    const img = new Image()
    img.onload = () => resolve(true)
    img.onerror = () => resolve(false)
    img.src = url
  });
}

async function onSelectImage(e: Event) {
  errors.value = []
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  const blobUrl = URL.createObjectURL(file)
  form.value.imageUrl = blobUrl 
  try {
    const data = await uploadProductImage(file)
    form.value.imageUrl = data.url || form.value.imageUrl
    // console.log(data.url)
  } catch (err) {
    errors.value = ['图片上传失败']
  } finally {
    uploading.value = false
  }
  URL.revokeObjectURL(blobUrl)
}

function triggerFileInput() {
  const input = document.getElementById('image-input') as HTMLInputElement | null;
  input?.click();
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
        <input style="display:none" id="image-input" type="file" accept="image/*" @change="onSelectImage" />
        <div class="form-actions span-2">
          <button type="button" class="soft-button" @click="triggerFileInput" :disabled="uploading">
            <Upload :size="18" />
            <span>{{ uploading ? '上传中...' : '图片' }}</span>
          </button>
          <button type="submit" class="black-button">
            <PackagePlus :size="18" />
            <span>上架</span>
          </button>
        </div>
        <div class="form-errors" v-if="errors.length">
          <ul>
            <li v-for="err in errors" :key="err">{{ err }}</li>
          </ul>
        </div>
      </form>
    </section>

    <aside class="merchant-preview">
      <section class="bento-card preview-card">
        <img :src="form.imageUrl" 
              referrerpolicy="no-referrer" 
              :alt="form.name" />
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
          <p>
            <template v-if="result">{{ result }}</template>
            <template v-else>商品保存后将自动提交向量索引任务。AI 索引可能存在短暂延迟，请稍候查看 `vector_index_status`。</template>
          </p>
        </div>
      </section>
    </aside>
  </div>
</template>
