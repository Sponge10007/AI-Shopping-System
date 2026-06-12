<script setup lang="ts">
import { Check, ShoppingBag, Sparkles } from 'lucide-vue-next'

const thumbnails = [1, 2, 3, 4]
</script>

<template>
  <div class="page detail-page">
    <section class="detail-gallery">
      <div class="detail-hero-image">
        <img
          src="https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=1200&q=80"
          alt="智能降噪耳机"
        />
      </div>
      <div class="thumb-grid">
        <button v-for="item in thumbnails" :key="item" type="button" class="thumb-tile" :title="`缩略图 ${item}`">
          <img
            src="https://images.unsplash.com/photo-1484704849700-f032a568e944?auto=format&fit=crop&w=400&q=80"
            alt=""
          />
        </button>
      </div>
    </section>

    <aside class="detail-info">
      <span class="ai-chip">AI Verified</span>
      <h1>智能降噪耳机 Pro Max</h1>
      <p class="detail-price">¥ 299.00</p>

      <section class="ai-reason-card">
        <Sparkles :size="18" />
        <div>
          <h3>为什么它适合你？</h3>
          <p>
            基于你搜索的“通勤”“降噪”“三百元预算”，这款耳机在续航、舒适度和价格区间上更贴合当前意图。
          </p>
        </div>
      </section>

      <div class="score-grid">
        <article>
          <span>AI 匹配度</span>
          <strong>98.5%</strong>
        </article>
        <article>
          <span>性能权重</span>
          <strong>均衡</strong>
        </article>
      </div>

      <ul class="detail-points">
        <li><Check :size="17" /> 主动降噪，适合地铁和办公室</li>
        <li><Check :size="17" /> 轻量佩戴，长时间使用压力低</li>
        <li><Check :size="17" /> 可加入 AI 对比清单</li>
      </ul>

      <div class="detail-actions">
        <button type="button" class="black-button full-button">
          <ShoppingBag :size="18" />
          <span>立即选购</span>
        </button>
        <RouterLink to="/compare" class="soft-button full-button">加入对比清单</RouterLink>
      </div>
    </aside>
  </div>
</template>
