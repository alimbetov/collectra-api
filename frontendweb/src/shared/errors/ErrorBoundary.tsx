import { Component, type ErrorInfo, type PropsWithChildren } from 'react';
import { ProblemDetailPanel } from './ProblemDetailPanel';

interface State { error: unknown | null }

export class ErrorBoundary extends Component<PropsWithChildren, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: unknown): State {
    return { error };
  }

  componentDidCatch(error: unknown, info: ErrorInfo) {
    // A telemetry adapter can consume this boundary later; never render stack traces to users.
    console.error('Unhandled frontend error', error, info.componentStack);
  }

  reset = () => this.setState({ error: null });

  render() {
    if (this.state.error) {
      return <main className="global-error"><ProblemDetailPanel error={this.state.error} onRetry={this.reset} /></main>;
    }
    return this.props.children;
  }
}
