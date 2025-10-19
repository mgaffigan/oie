import { useQuery } from '@tanstack/react-query';
import { LoginPage } from './pages/login/LoginPage';
import { createSession, SessionContext } from './services/Session';
import { MainRouter } from './Routes';

// Handles login and delegates to the main router
export default function App() {
  const { isPending, data: session, refetch } = useQuery({
    queryKey: ['user'],
    retry: false,
    queryFn: createSession,
  });

  if (isPending) {
    return <span>Loading...</span>;
  }

  if (!session) {
    return <LoginPage onLoginSuccess={refetch} />
  }

  return <SessionContext value={session}>
    <MainRouter />
  </SessionContext>;
}
