import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { LogIn } from 'lucide-react';
import { login } from '../lib/api';

export default function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError('');
    setLoading(true);
    try {
      await login(email, password);
      navigate('/app/presentations', { replace: true });
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : '登录失败');
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-panel">
        <h1 className="page-title">登录 PPT Agent</h1>
        <p className="subtitle">进入在线编辑器和 Agent 生成工作台</p>
        <form className="form" onSubmit={submit}>
          {error && <div className="error">{error}</div>}
          <label className="field">
            邮箱
            <input className="input" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
          </label>
          <label className="field">
            密码
            <input className="input" type="password" value={password} onChange={(event) => setPassword(event.target.value)} required />
          </label>
          <button className="button" disabled={loading}>
            <LogIn size={18} />
            {loading ? '登录中' : '登录'}
          </button>
          <Link className="ghost-button" to="/register">
            创建账号
          </Link>
        </form>
      </section>
    </main>
  );
}
