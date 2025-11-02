import { useRef } from "react";
import { useParams } from "react-router";
import { useSession } from "../../services/Session";
import { StandardLayout } from "../../layout/StandardLayout";
import { usePluginMount, type LowerThirdHookRegistration } from "../../services/PluginRegistry";
import css from './DashboardPage.module.scss';

export function LowerThirdBody(props: { hook: LowerThirdHookRegistration }) {
    const mountRef = useRef<HTMLDivElement>(null);

    usePluginMount(mountRef, async (target) => {
        const mountPlugin = await props.hook.loadAsync();
        return mountPlugin(target);
    }, [props.hook]);

    return <div ref={mountRef} className={css.mountPoint} />;
}

export function LowerThirdPopoutPage() {
    const { hookId } = useParams<{ hookId: string }>();
    const sess = useSession();

    const hook = sess.plugins.getLowerThirdHooks().find(h => h.hookId === hookId);

    if (!hook) {
        return <StandardLayout title="Lower Third">
            <p>Lower Third {hookId} not found.</p>
        </StandardLayout>;
    }
    
    return <StandardLayout title={hook.header}>
        <div className="card p-2" style={{ height: '100%' }}>
            <LowerThirdBody hook={hook} />
        </div>
    </StandardLayout>;
}
