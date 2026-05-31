<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PackagePlus, Sparkles, Upload } from 'lucide-vue-next'
import { getMerchantProduct, editMerchantProduct, uploadProductImage } from '../services/api'

const route = useRoute()
const router = useRouter()
const id = String(route.params.id || '')

const form = ref<any>({ name: '', description: '', price: '', stock: 0, tags: '', imageUrl: '' })
const errors = ref<string[]>([])
const result = ref('')
const uploading = ref(false)

onMounted(async () => {
  if (!id) return
  const p = await getMerchantProduct(id)
  form.value = {
    name: p.name,
    description: p.description,
    price: p.price,
    stock: p.stock,
    tags: (p.tags || []).join(','),
    imageUrl: p.image_urls?.[0] || p.image_urls || '',
  }
})

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

  try{
    const response = await editMerchantProduct(id, {
      name: form.value.name,
      description: form.value.description,
      price: form.value.price,
      stock: form.value.stock,
      tags: form.value.tags.split(',').map((t: string) => t.trim()).filter(Boolean),
      image_urls: [form.value.imageUrl],
    })
    result.value = '编辑成功！'
    alert('编辑成功')
  }
  catch{
    result.value = '编辑失败！'
    alert('编辑失败')
  }

}


async function isImageUrl(url: string): Promise<boolean> {
  
  //测试环境，上线要注释
  if(url.startsWith("https://example.com/")){
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
        <h1>商品编辑</h1>
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
            <span>保存</span>
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
          <h3>编辑状态</h3>
          <p>
            <template v-if="result">{{ result }}</template>
            <template v-else>暂未修改</template>
          </p>
        </div>
      </section>
    </aside>
  </div>
</template>

