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
