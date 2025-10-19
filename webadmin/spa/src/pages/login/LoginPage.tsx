import { useState, useMemo } from 'react';
import { login } from '../../services/Services';
import css from './LoginPage.module.css';
import logo from '../../assets/login_logo.svg';
import { useMutation } from '@tanstack/react-query';

export function LoginPage(params: { onLoginSuccess: () => void }) {
    const [error, setError] = useState<string | undefined>();
    const { mutate, isPending } = useMutation({
        mutationFn: login,
        onSuccess: (status) => {
            if (status.status === 'SUCCESS' || status.status === 'SUCCESS_GRACE_PERIOD') {
                params.onLoginSuccess();
            } else {
                setError(status.message);
            }
        },
        onError: (error: any) => { setError(error.message || 'Login failed'); }
    });

    const handleLogin = useMemo(() => {
        return (e: React.FormEvent<HTMLFormElement>) => {
            e.preventDefault();
            setError(undefined);

            const formData = new FormData(e.currentTarget);
            mutate({
                username: formData.get('username') as string,
                password: formData.get('password') as string
            });
        };
    }, [mutate]);

    return (<div className={css.loginPage}>
        <div id={css.loginCard}>
            <img src={logo} alt="Open Integration Engine Logo" id="logo" />

            {error && <div id={css.alertBlock}>{error}</div>}

            <form onSubmit={handleLogin}>
                <label htmlFor="username">Username</label>
                <input id="username" type="text" name="username" autoFocus required />

                <label htmlFor="password">Password</label>
                <input id="password" type="password" name="password" required />

                <input type="submit" value="Sign in" disabled={isPending} />
            </form>
        </div>
    </div>);
}
