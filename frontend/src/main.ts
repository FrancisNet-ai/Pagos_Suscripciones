import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

bootstrapApplication(App, appConfig).catch((err: unknown) => {
  console.error(err);
  document.querySelector('[data-bootstrap-loader]')?.remove();
  const error = document.getElementById('app-startup-error');
  if (error) {
    const retry = error.querySelector('a');
    if (retry) retry.href = window.location.href;
    error.hidden = false;
  }
});
