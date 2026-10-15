<template>
  <div class="data-table">
    <!-- 工具栏 -->
    <div class="table-toolbar" v-if="showToolbar">
      <div class="toolbar-left">
        <slot name="toolbar-left" />
      </div>
      <div class="toolbar-right">
        <slot name="toolbar-right" />
      </div>
    </div>

    <!-- 表格 -->
    <el-table
      ref="tableRef"
      :data="data"
      v-loading="loading"
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
      border
      stripe
      highlight-current-row
      style="width: 100%"
    >
      <!-- 多选列 -->
      <el-table-column v-if="selectable" type="selection" width="50" fixed="left" />

      <!-- 数据列 -->
      <template v-for="col in columns" :key="col.prop">
        <!-- 序号列 -->
        <el-table-column
          v-if="col.render === 'index'"
          type="index"
          :label="col.label"
          :width="col.width || 60"
          align="center"
        />

        <!-- 普通省略列 -->
        <el-table-column
          v-else-if="col.render === 'ellipsis'"
          :prop="col.prop"
          :label="col.label"
          :width="col.width"
          :min-width="col.minWidth"
          :sortable="col.sortable ? 'custom' : false"
          show-overflow-tooltip
        />

        <!-- 标签列 -->
        <el-table-column
          v-else-if="col.render === 'tags'"
          :prop="col.prop"
          :label="col.label"
          :width="col.width"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="getTagType(col.renderOptions?.colorMap, row[col.prop])"
              size="small"
            >
              {{ row[col.prop] }}
            </el-tag>
          </template>
        </el-table-column>

        <!-- 星级列 -->
        <el-table-column
          v-else-if="col.render === 'stars'"
          :prop="col.prop"
          :label="col.label"
          :width="col.width"
          align="center"
        >
          <template #default="{ row }">
            <span class="stars">
              <span
                v-for="i in 5"
                :key="i"
                :class="['star', i <= row[col.prop] ? 'filled' : '']"
              >★</span>
            </span>
          </template>
        </el-table-column>

        <!-- 进度条列 -->
        <el-table-column
          v-else-if="col.render === 'progress'"
          :prop="col.prop"
          :label="col.label"
          :width="col.width"
          :sortable="col.sortable ? 'custom' : false"
        >
          <template #default="{ row }">
            <div class="progress-cell">
              <el-progress
                :percentage="row[col.prop]"
                :status="getProgressStatus(row[col.prop])"
                :stroke-width="8"
                style="flex: 1"
              />
              <span class="progress-text">{{ row[col.prop] }}%</span>
            </div>
          </template>
        </el-table-column>

        <!-- 状态列 -->
        <el-table-column
          v-else-if="col.render === 'status'"
          :prop="col.prop"
          :label="col.label"
          :width="col.width"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="getTagType(col.renderOptions?.colorMap, row[col.prop])"
              size="small"
              effect="plain"
            >
              {{ row[col.prop] }}
            </el-tag>
          </template>
        </el-table-column>

        <!-- 格式化时间列 -->
        <el-table-column
          v-else-if="col.render === 'datetime'"
          :prop="col.prop"
          :label="col.label"
          :width="col.width"
          :sortable="col.sortable ? 'custom' : false"
        >
          <template #default="{ row }">
            {{ formatDate(row[col.prop]) }}
          </template>
        </el-table-column>

        <!-- 相对时间列 -->
        <el-table-column
          v-else-if="col.render === 'relativeTime'"
          :prop="col.prop"
          :label="col.label"
          :width="col.width"
        >
          <template #default="{ row }">
            <el-tooltip :content="formatDate(row[col.prop])" placement="top">
              <span>{{ getRelativeTime(row[col.prop]) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>

        <!-- 操作列 -->
        <el-table-column
          v-else-if="col.render === 'actions'"
          :label="col.label"
          :width="col.width"
          fixed="right"
          align="center"
        >
          <template #default="{ row }">
            <slot name="actions" :row="row" />
          </template>
        </el-table-column>

        <!-- 数字可排序列 -->
        <el-table-column
          v-else
          :prop="col.prop"
          :label="col.label"
          :width="col.width"
          :min-width="col.minWidth"
          :sortable="col.sortable ? 'custom' : false"
          :show-overflow-tooltip="col.showOverflowTooltip"
          align="center"
        />
      </template>
    </el-table>

    <!-- 分页 -->
    <div class="table-pagination" v-if="showPagination">
      <el-pagination
        v-model:current-page="innerPage.currentPage"
        v-model:page-size="innerPage.pageSize"
        :page-sizes="innerPage.pageSizes"
        :total="innerPage.total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="handlePageSizeChange"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { TableColumn, Pagination } from '@/types'
import { formatDateTime, relativeTime, statusColorMap, typeColorMap, directionColorMap, roleColorMap } from '@/utils/helpers'

interface Props {
  data: any[]
  columns: TableColumn[]
  loading?: boolean
  selectable?: boolean
  showPagination?: boolean
  showToolbar?: boolean
  pagination?: Pagination
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  selectable: true,
  showPagination: true,
  showToolbar: true
})

const emit = defineEmits<{
  (e: 'selectionChange', rows: any[]): void
  (e: 'sortChange', field: string, order: 'ascending' | 'descending' | null): void
  (e: 'pageChange', page: number): void
  (e: 'pageSizeChange', size: number): void
}>()

const tableRef = ref()

const innerPage = computed(() => props.pagination || {
  currentPage: 1,
  pageSize: 10,
  total: 0,
  pageSizes: [10, 20, 50, 100]
})

function handleSelectionChange(rows: any[]) {
  emit('selectionChange', rows)
}

function handleSortChange({ prop, order }: { prop: string; order: any }) {
  emit('sortChange', prop, order)
}

function handlePageChange(page: number) {
  emit('pageChange', page)
}

function handlePageSizeChange(size: number) {
  emit('pageSizeChange', size)
}

const allColorMaps: Record<string, Record<string, string>> = {
  statusColorMap,
  typeColorMap,
  directionColorMap,
  roleColorMap
}

function getTagType(colorMap: Record<string, string> | string | undefined, value: string): string {
  let map: Record<string, string> | undefined
  if (typeof colorMap === 'string') {
    map = allColorMaps[colorMap]
  } else {
    map = colorMap
  }
  if (!map) return ''
  return map[value] || ''
}

function getProgressStatus(val: number): '' | 'success' | 'exception' | 'warning' {
  if (val >= 80) return 'success'
  if (val >= 50) return ''
  if (val >= 30) return 'warning'
  return 'exception'
}

function formatDate(str: string): string {
  return formatDateTime(str)
}

function getRelativeTime(str: string): string {
  return relativeTime(str)
}

defineExpose({ tableRef })
</script>

<style scoped lang="scss">
.data-table {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.stars {
  .star {
    color: #ddd;
    font-size: 16px;
    &.filled {
      color: #f5a623;
    }
  }
}

.progress-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  .progress-text {
    font-size: 12px;
    color: #666;
    width: 36px;
    text-align: right;
  }
}

.table-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 8px 0;
}
</style>
