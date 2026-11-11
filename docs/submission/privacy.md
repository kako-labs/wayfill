# Privacy and data handling — predeployment draft

This describes the intended boundary and review requirements. It is not a published privacy policy or a claim that a service is deployed. Confirm the final implementation and hosting configuration before collecting real data.

| Data | Purpose and boundary | Handling requirement |
| --- | --- | --- |
| Fuel/range and vehicle motion | Local decision input; genuine adapter capabilities may be unavailable | Use only necessary current context; show Unknown/Unsupported on missing capability; never substitute demo fixtures |
| Location and destination/route context | Nearby discovery and navigation decisions | Ask for applicable OS permission; minimize precision and retention; document exactly what a deployed gateway receives |
| Reserve/motion preferences | Local durable behavior settings | Keep normal and demo namespaces distinct; inspect final storage retention and reset behavior |
| Event/request identifiers | Deduplication, correlation and no replay across recovery | Avoid identifiers tied to a person or vehicle; retain only the bounded state required for behavior |
| Provider station catalog/price/provenance | Real provider data through server boundary | Honour provider terms, timestamps, cache/quota policy and attribution; raw catalog is not route reachability |
| Provider credentials | Server-side provider authentication | Never deliver to Android/web/source ZIP; redact errors and logs; rotate outside source control |
| Debug fixture data | Explicitly labelled native demonstration | Synthetic neutral values; separate store; excluded from release implementation |
| Reports/screenshots/logs | Engineering verification | Use synthetic fixtures; redact personal routes, tokens and notifications; review before upload/share |

Before deployment, document the actual gateway host/operator, HTTPS trust, request fields, legal basis/consent where applicable, access controls, log fields, cache expiry, backup policy, deletion path and incident contact. These are unresolved deployment decisions. Do not promise zero server logging or a retention duration without checking the deployed system.

OS permission denial and monitoring stop must remain visible and actionable. Monitoring is explicit opt-in; boot/update asks the user to resume. Test removal/revocation and process-recovery behavior before describing it as verified. No analytics, advertising or secondary data use is authorized by this draft.

For a public demo, use a fresh synthetic session, hide notification contents, avoid real home/work destinations and inspect the entire recording frame by frame. For source delivery, review the tracked source and the extracted ZIP; a tracked API token with an ordinary filename can defeat filename filtering. CI artifacts are retained for 14 days by the workflow; repository access permissions still govern visibility. Review retention/access before changing repository visibility or distributing artifacts.
