import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import AlbumDetail from '../AlbumDetail.vue'
import zhCN from '../../../locales/zh-CN.json'

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  useRoute: () => ({ query: {} }),
}))

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': zhCN },
})

type Resolver = (v: unknown) => void

function mkAlbum(id: number) {
  return { id, name: `相册${id}`, photoCount: 2 }
}

/**
 * 可控 fetch：每个请求挂起，由测试按序手动 resolve（内容 id 从 URL 的相册 id 派生），
 * 用于构造「旧相册响应晚于新相册响应到达」的竞态。
 */
function deferredFetch() {
  const resolvers: Resolver[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => {
      const m = String(url).match(/albums\/(\d+)/)
      const albumId = m ? Number(m[1]) : 0
      return new Promise((resolve) => {
        resolvers.push(() =>
          resolve({
            status: 200,
            ok: true,
            headers: new Headers(),
            json: async () => ({
              code: 200,
              data: {
                content: [
                  { id: albumId * 10 + 1, name: `a${albumId}-1` },
                  { id: albumId * 10 + 2, name: `a${albumId}-2` },
                ],
                totalPages: 1,
                totalElements: 2,
                last: true,
              },
            }),
          }),
        )
      })
    }),
  )
  return resolvers
}

function mountDetail(album = mkAlbum(1)) {
  return mount(AlbumDetail, {
    props: { album },
    global: {
      plugins: [createPinia(), i18n],
      stubs: { PhotoGrid: true, EmptyState: true },
    },
  })
}

describe('AlbumDetail — 分页加载', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  it('挂载即加载第一页（watch immediate 承担首载）', async () => {
    const resolvers = deferredFetch()
    const wrapper = mountDetail()
    await flushPromises()
    expect(resolvers).toHaveLength(1)
    expect(vi.mocked(fetch).mock.calls[0][0]).toContain(
      '/api/v1/albums/1/photos?page=0&size=20&sort=createdAt,desc',
    )
    resolvers[0](null)
    await flushPromises()
    expect((wrapper.vm as never as { photos: { id: number }[] }).photos).toHaveLength(2)
  })

  it('竞态回归：滚动加载中切换相册，旧相册的迟到响应被丢弃，不混入新列表', async () => {
    const resolvers = deferredFetch()
    const wrapper = mountDetail(mkAlbum(1))
    await flushPromises()
    expect(resolvers).toHaveLength(1)

    // 相册 1 的请求在途时切换到相册 2 → 旧请求作废、新请求发出
    await wrapper.setProps({ album: mkAlbum(2) })
    await flushPromises()
    expect(resolvers).toHaveLength(2)

    // 新相册响应先到 → 列表 = 相册 2 的照片
    resolvers[1](null)
    await flushPromises()
    const photos = () => (wrapper.vm as never as { photos: { id: number }[] }).photos
    expect(photos()).toHaveLength(2)
    expect(photos()[0].id).toBe(21)

    // 旧相册的迟到响应后到 → requestId 守卫丢弃，列表保持相册 2 内容
    resolvers[0](null)
    await flushPromises()
    expect(photos()).toHaveLength(2)
    expect(photos().map((p) => p.id)).toEqual([21, 22])
  })
})
