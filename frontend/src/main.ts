import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

bootstrapApplication(App, appConfig).catch((err: unknown) => {
  console.error(err);
  document.querySelector('[data-bootstrap-loader]')?.remove();
  const template = document.querySelector<HTMLTemplateElement>('#app-startup-error');
  if (template) {
    const error = template.content.cloneNode(true) as DocumentFragment;
    const retry = error.querySelector('a');
    if (retry) retry.href = window.location.href;
    const root = document.querySelector('app-root');
    if (root) root.replaceChildren(error);
    else document.body.append(error);
  }
});
