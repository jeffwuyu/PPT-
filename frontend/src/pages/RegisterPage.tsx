import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { UserPlus } from 'lucide-react';
import { register } from '../lib/api';

export default function RegisterPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError('');
    setLoading(true);
    try {
      await register(email, username, password);
      navigate('/app/presentations', { replace: true });
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : '注册失败');
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-panel">
        <h1 className="page-title">创建账号</h1>
        <p className="subtitle">注册后可直接创建和生成 PPT</p>
        <form className="form" onSubmit={submit}>
          {error && <div className="error">{error}</div>}
          <label className="field">
            邮箱
            <input className="input" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
          </label>
          <label className="field">
            用户名
            <input className="input" value={username} onChange={(event) => setUsername(event.target.value)} minLength={3} required />
          </label>
          <label className="field">
            密码
            <input className="input" type="password" value={password} onChange={(event) => setPassword(event.target.value)} minLength={8} required />
          </label>
          <button className="button" disabled={loading}>
            <UserPlus size={18} />
            {loading ? '创建中' : '注册'}
          </button>
          <Link className="ghost-button" to="/login">
            返回登录
          </Link>
        </form>
      </section>
    </main>
  );
}
