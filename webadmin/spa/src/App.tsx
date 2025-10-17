import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Client } from './Services';
import { LoginPage } from './Login';
import { SessionContext, useSession } from './Session';
import type { SessionContextType } from './Session';

function ExamplePage() {
  const sess = useSession();

  return <div>Hello {sess.user.firstName}!</div>
}

export default function App() {
  const { isPending, isError, data: user, refetch } = useQuery({
    queryKey: ['user'],
    retry: false,
    queryFn: async () => {
      const { data: currentUser } = await Client.GET('/users/current');
      if (!currentUser) {
        throw new Error('Not authenticated');
      }
      return currentUser;
    },
  });

  if (isPending) {
    return <span>Loading...</span>;
  }

  if (isError) {
    return <LoginPage onLoginSuccess={refetch} />
  }

  const session = useMemo<SessionContextType>(() => ({ user }), [user]);

  return <SessionContext value={session}>
    <ExamplePage />
  </SessionContext>;
}
