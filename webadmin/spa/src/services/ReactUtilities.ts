import { useEffect, type DependencyList } from "react";

export function useAsyncEffect(effect: () => Promise<(() => void) | void>, deps: DependencyList): void {
    useEffect(() => {
        let isCancelled = false;
        let cancelFunction = () => { };
        effect().then(newCancel => {
            if (isCancelled) {
                newCancel?.();
                return;
            }
            
            cancelFunction = newCancel ?? cancelFunction;
        });
        return () => { isCancelled = true; cancelFunction(); }
    }, deps);
}

export function usePeriodicAsyncEffect(
    effect: () => Promise<void>, 
    onError: (error: unknown) => void, 
    intervalMs: number, 
    deps: DependencyList
): void {
    useEffect(() => {
        let isCancelled = false;
        let timeoutId: number | undefined = undefined;

        const runEffect = async () => {
            if (isCancelled) return;
            try {
                await effect();
            } catch (e) {
                try {
                    onError(e);
                } catch { /* Ignore errors in error handler */ }
            }
            if (isCancelled) return;
            timeoutId = setTimeout(runEffect, intervalMs);
        };

        runEffect();

        return () => { isCancelled = true; clearTimeout(timeoutId); }
    }, [intervalMs, ...deps]);
}
