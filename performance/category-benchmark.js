
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Các chỉ số dùng cho báo cáo
const latency = new Trend('category_latency_ms', true);
const errors = new Rate('category_error_rate');
const requests = new Counter('category_requests');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  vus: 1,
  duration: '10s',
};

// Đăng nhập ADMIN một lần trước khi kiểm thử
export function setup() {
  const response = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({
      email: 'admin@example.com',
      password: __ENV.ADMIN_PASSWORD,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
      },
    }
  );

  if (response.status !== 200) {
    throw new Error(`Dang nhap that bai: HTTP ${response.status}`);
  }

  return {
    token: response.json('accessToken'),
  };
}

// Gửi request liên tục trong thời gian kiểm thử
export default function (data) {
  const response = http.get(
    `${BASE_URL}/api/categories`,
    {
      headers: {
        Authorization: `Bearer ${data.token}`,
      },
    }
  );

  const success = check(response, {
    'GET categories returns 200': (r) => r.status === 200,
  });

  latency.add(response.timings.duration);
  errors.add(!success);
  requests.add(1);
}
