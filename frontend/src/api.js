// 封装 fetch，写操作自动附带 JWT；401 时触发重新锁定

const ADMIN_PASSWORD = 'photoadmin';

export async function api(url, options = {}) {
  const token = localStorage.getItem('jwt_token');
  const headers = { ...options.headers };

  // 所有请求自动附 token
  if (token) {
    headers['Authorization'] = 'Bearer ' + token;
  }

  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  const res = await fetch(url, { ...options, headers });

  // 401 → token 过期，触发重新锁定
  if (res.status === 401) {
    localStorage.removeItem('jwt_token');
    localStorage.removeItem('konami_unlocked');
    window.location.reload();
    throw new Error('登录已过期，请重新解锁');
  }

  return res;
}

/** Konami 解锁后拿 JWT */
export async function requestToken() {
  const res = await fetch('/api/auth/unlock', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password: ADMIN_PASSWORD })
  });
  if (!res.ok) throw new Error('认证失败');
  const data = await res.json();
  return data.data.token;
}
