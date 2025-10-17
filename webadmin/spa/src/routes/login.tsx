import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import {
    Card,
    CardContent,
    CardDescription,
    CardFooter,
    CardHeader,
    CardTitle,
} from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { loginWithBasic } from '@/lib/auth';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { Eye, EyeOff, Loader2, ShieldCheck } from 'lucide-react';
import React from 'react';

export const Route = createFileRoute('/login')({
    component: LoginPage,
});

export default function LoginPage() {
    const navigate = useNavigate();
    const [pending, setPending] = React.useState(false);
    const [error, setError] = React.useState<string | null>(null);
    const [showPw, setShowPw] = React.useState(false);

    async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
        e.preventDefault();
        setError(null);
        const fd = new FormData(e.currentTarget);
        const username = String(fd.get('username') ?? '');
        const password = String(fd.get('password') ?? '');

        if (!username || !password) {
            setError('Please enter both username and password.');
            return;
        }

        setPending(true);
        try {
            await loginWithBasic({ username, password });
            navigate({ to: '/dashboard' });
        } catch (err: any) {
            setError(err?.message || 'Login failed');
        } finally {
            setPending(false);
        }
    }

    return (
        <div className="min-h-svh grid place-items-center bg-gradient-to-b from-background to-muted/60 px-4">
            <Card className="w-full max-w-md shadow-lg">
                <CardHeader className="space-y-2">
                    <div className="flex items-center gap-2">
                        <div className="size-9 grid place-items-center rounded-full bg-primary/10">
                            <ShieldCheck
                                className="size-5 text-primary"
                                aria-hidden="true"
                            />
                        </div>
                        <CardTitle className="text-xl">Sign in</CardTitle>
                    </div>
                    <CardDescription>
                        Use your OIE credentials. Your session cookie is set by
                        the server.
                    </CardDescription>
                </CardHeader>

                <CardContent>
                    {error && (
                        <Alert
                            variant="destructive"
                            role="alert"
                            aria-live="assertive"
                            className="mb-4"
                        >
                            <AlertDescription>{error}</AlertDescription>
                        </Alert>
                    )}

                    <form onSubmit={onSubmit} className="grid gap-4">
                        <div className="grid gap-2">
                            <Label htmlFor="username">Username</Label>
                            <Input
                                id="username"
                                name="username"
                                type="text"
                                autoComplete="username"
                                disabled={pending}
                                placeholder="your.username"
                                required
                            />
                        </div>

                        <div className="grid gap-2">
                            <div className="flex items-center justify-between">
                                <Label htmlFor="password">Password</Label>
                                {/* Optional: <a className="text-sm text-muted-foreground hover:underline" href="/forgot">Forgot?</a> */}
                            </div>
                            <div className="relative">
                                <Input
                                    id="password"
                                    name="password"
                                    type={showPw ? 'text' : 'password'}
                                    autoComplete="current-password"
                                    disabled={pending}
                                    placeholder="••••••••"
                                    required
                                    className="pr-10"
                                />
                                <button
                                    type="button"
                                    onClick={() => setShowPw((s) => !s)}
                                    className="absolute inset-y-0 right-2 grid place-items-center px-1 text-muted-foreground hover:text-foreground"
                                    aria-label={
                                        showPw
                                            ? 'Hide password'
                                            : 'Show password'
                                    }
                                    tabIndex={-1}
                                >
                                    {showPw ? (
                                        <EyeOff className="size-4" />
                                    ) : (
                                        <Eye className="size-4" />
                                    )}
                                </button>
                            </div>
                        </div>

                        <div className="flex items-center justify-between">
                            <label className="flex items-center gap-2 text-sm text-muted-foreground">
                                <Checkbox
                                    id="remember"
                                    name="remember"
                                    disabled={pending}
                                />
                                <span>Remember me on this device</span>
                            </label>
                        </div>

                        <Button
                            type="submit"
                            className="w-full"
                            disabled={pending}
                        >
                            {pending ? (
                                <>
                                    <Loader2 className="mr-2 size-4 animate-spin" />
                                    Signing in…
                                </>
                            ) : (
                                'Sign in'
                            )}
                        </Button>
                    </form>
                </CardContent>

                <CardFooter className="flex justify-center">
                    <p className="text-xs text-muted-foreground">
                        Ensure you’re on a secure connection.
                    </p>
                </CardFooter>
            </Card>
        </div>
    );
}
