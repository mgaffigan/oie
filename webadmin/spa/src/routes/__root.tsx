import { TanStackDevtools } from '@tanstack/react-devtools';
import { Outlet, createRootRoute } from '@tanstack/react-router';
import { TanStackRouterDevtoolsPanel } from '@tanstack/react-router-devtools';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import Header from '../components/Header';

// Create a client
const queryClient = new QueryClient();

export const Route = createRootRoute({
    component: () => (
        // Provide the client to your App
        <QueryClientProvider client={queryClient}>
            <>
                <Header />
                <Outlet />
                <TanStackDevtools
                    config={{
                        position: 'bottom-right',
                    }}
                    plugins={[
                        {
                            name: 'Tanstack Router',
                            render: <TanStackRouterDevtoolsPanel />,
                        },
                    ]}
                />
            </>
        </QueryClientProvider>
    ),
});
