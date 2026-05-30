<script setup lang="ts">
import { Check, Save, Trophy } from 'lucide-vue-next'

const points = ['降噪能力高于均值 30%', '续航更适合通勤与学习', '完全落入你的预算区间']
const attrs = ['音质', '续航', '舒适度', '性价比']
</script>

<template>
  <div class="page compare-page">
    <header class="page-title centered">
      <p>针对您的意图优化</p>
      <h1>“寻找适合通勤的高性价比耳机”</h1>
      <span>AI 已结合你的历史偏好对降噪、续航和价格进行加权分析。</span>
    </header>

    <section class="compare-layout">
      <aside class="compare-sidebar">
        <article class="bento-card ai-final">
          <div class="trophy-box">
            <Trophy :size="26" />
          </div>
          <h2>AI 最终建议</h2>
          <p>
            综合权衡，蓝牙降噪耳机更适合你。它的价格更低，且在通勤场景中的降噪收益更明显。
          </p>
          <div class="point-list">
            <span v-for="point in points" :key="point">
              <Check :size="15" />
              {{ point }}
            </span>
          </div>
        </article>

        <article class="bento-card attr-card">
          <h3>核心属性分布</h3>
          <div v-for="(attr, index) in attrs" :key="attr" class="attr-row">
            <div>
              <span>{{ attr }}</span>
              <strong>领先 {{ 8 + index * 3 }}%</strong>
            </div>
            <div class="bar-track">
              <div :style="{ width: `${72 + index * 6}%` }"></div>
            </div>
          </div>
        </article>
      </aside>

      <section class="compare-main">
        <div class="compare-card">
          <article class="compare-product winner">
            <span class="winner-badge">Best Match</span>
            <div class="compare-image">
              <img
                src="https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=80"
                alt="蓝牙降噪耳机"
              />
            </div>
            <h3>蓝牙降噪耳机</h3>
            <p class="blue-price">¥ 299</p>
            <div class="comment-box">通勤、预算和舒适度匹配度最高，适合作为默认推荐。</div>
          </article>

          <article class="compare-product">
            <div class="compare-image">
              <img
                src="https://images.unsplash.com/photo-1484704849700-f032a568e944?auto=format&fit=crop&w=900&q=80"
                alt="头戴耳机"
              />
            </div>
            <h3>头戴监听耳机</h3>
            <p>¥ 599</p>
            <div class="comment-box">音质优势明显，但价格和便携性不如第一款。</div>
          </article>
        </div>

        <div class="compare-actions">
          <button type="button" class="black-button">将 Best Match 加入购物车</button>
          <button type="button" class="soft-button">
            <Save :size="17" />
            <span>保存报告</span>
          </button>
        </div>
      </section>
    </section>
  </div>
</template>
