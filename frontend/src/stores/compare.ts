import { reactive } from 'vue'

const STORAGE_KEY = 'ai-shopping-compare-products'
export const MAX_COMPARE_PRODUCTS = 4

function loadProductIds(): string[] {
  try {
    const value = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
    if (Array.isArray(value)) {
      return [...new Set(value.map(String).filter(Boolean))].slice(0, MAX_COMPARE_PRODUCTS)
    }
  } catch {
    // Ignore malformed local data.
  }
  return []
}

export const compareState = reactive({
  productIds: loadProductIds(),
})

function persist() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(compareState.productIds))
  } catch {
    // The in-memory list still works when browser storage is unavailable.
  }
}

export function addCompareProduct(productId: string): boolean {
  if (compareState.productIds.includes(productId)) return true
  if (compareState.productIds.length >= MAX_COMPARE_PRODUCTS) return false
  compareState.productIds.push(productId)
  persist()
  return true
}

export function removeCompareProduct(productId: string) {
  compareState.productIds = compareState.productIds.filter((id) => id !== productId)
  persist()
}

export function clearCompareProducts() {
  compareState.productIds = []
  persist()
}
