<script setup lang="ts">
import { Heart, Package, Sparkles } from 'lucide-vue-next'
import { reactive } from 'vue'

const settings = reactive([
  { name: '自动多模态识别', value: true },
  { name: '模糊意图增强', value: true },
  { name: '隐私数据加密', value: true },
  { name: '实时降价提醒', value: false },
])

const tags = ['极简主义', '高性能数码', '环保材质', '北欧色彩', '人体工学', '智能家居']
</script>

<template>
  <div class="page profile-page">
    <header class="profile-header">
      <div class="avatar-mark">U</div>
      <div>
        <p>Profile</p>
        <h1>你好，探索者</h1>
        <span>账户状态：省钱购物中......</span>
      </div>
      <div class="profile-actions">
        <button type="button" class="soft-button">编辑资料</button>
        <button type="button" class="black-button">会员中心</button>
      </div>
    </header>

    <section class="profile-grid">
      <article class="bento-card gene-card">
        <div class="card-topline">
          <div>
            <h2>AI 购物基因</h2>
            <p>基于 124 次语义检索生成的画像</p>
          </div>
          <div class="gradient-icon">
            <Sparkles :size="20" />
          </div>
        </div>
        <div class="tag-cloud">
          <span v-for="tag in tags" :key="tag">{{ tag }}</span>
          <strong>+ 正在挖掘新偏好</strong>
        </div>
        <p class="blue-note">AI 观察到你近期对“户外办公”表现出兴趣，已调整首页推荐权重。</p>
      </article>

      <article class="bento-card profile-side-card">
        <Package :size="24" />
        <h2>进行中的订单</h2>
        <p>智能感应台灯 Pro 正在运输中。</p>
        <RouterLink to="/orders" class="soft-button full-button">查看全部订单</RouterLink>
      </article>

      <article class="bento-card settings-card">
        <h2>智能助理偏好</h2>
        <div v-for="setting in settings" :key="setting.name" class="setting-row">
          <span>{{ setting.name }}</span>
          <button
            type="button"
            class="switch"
            :class="{ on: setting.value }"
            :title="setting.name"
            @click="setting.value = !setting.value"
          >
            <i></i>
          </button>
        </div>
      </article>

      <article class="bento-card wish-card">
        <div class="card-topline">
          <h2>愿望清单</h2>
          <Heart :size="20" />
        </div>
        <div class="wish-row">
          <div v-for="item in 4" :key="item">
            <div class="wish-image">ITEM</div>
            <strong>智能配件 {{ item }}</strong>
            <span>¥ 1,299</span>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>
