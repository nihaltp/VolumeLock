import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/app_state.dart';
import '../services/volume_service.dart';

class AppVolumeLockScreen extends StatefulWidget {
  const AppVolumeLockScreen({super.key});

  @override
  State<AppVolumeLockScreen> createState() => _AppVolumeLockScreenState();
}

class _AppVolumeLockScreenState extends State<AppVolumeLockScreen> {
  bool _loading = false;
  bool _accessibilityGranted = false;
  String _search = '';

  @override
  void initState() {
    super.initState();
    _checkAccessibility();
  }

  Future<void> _checkAccessibility() async {
    final ok = await VolumeService.isAccessibilityServiceEnabled();
    if (mounted) setState(() => _accessibilityGranted = ok);
  }

  Future<void> _loadApps() async {
    setState(() => _loading = true);
    await context.read<AppState>().loadInstalledApps();
    if (mounted) setState(() => _loading = false);
  }

  Future<void> _toggleAppVolumeLock(AppState state, bool value) async {
    if (value && !_accessibilityGranted) {
      final grant = await showDialog<bool>(
        context: context,
        builder: (_) => AlertDialog(
          title: const Text('Accessibility Permission Required'),
          content: const Text(
            'App Volume Lock uses the Accessibility Service to detect which '
            'app is in the foreground.\n\nPlease enable "Volume Lock" in '
            'Accessibility Settings → Installed Services.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Open Settings'),
            ),
          ],
        ),
      );
      if (grant == true) {
        await VolumeService.openAccessibilitySettings();
      }
      // Re-check after returning from settings.
      await _checkAccessibility();
      if (!_accessibilityGranted) return;
    }

    await state.setAppVolumeLockEnabled(value);
    if (value && state.installedApps.isEmpty) {
      await _loadApps();
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final state = context.watch<AppState>();
    final enabled = state.appVolumeLockEnabled;
    final apps = state.installedApps
        .where((a) =>
            _search.isEmpty ||
            a.appName.toLowerCase().contains(_search.toLowerCase()) ||
            a.packageName.toLowerCase().contains(_search.toLowerCase()))
        .toList()
      ..sort((a, b) {
        // Tracked apps first, then alphabetical.
        if (a.isTracked != b.isTracked) return a.isTracked ? -1 : 1;
        return a.appName.compareTo(b.appName);
      });

    return Scaffold(
      appBar: AppBar(
        title: const Text('App Volume Lock'),
        backgroundColor: theme.colorScheme.primary,
        foregroundColor: theme.colorScheme.onPrimary,
      ),
      body: Column(
        children: [
          // Toggle & accessibility warning
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
            child: Column(
              children: [
                Card(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 20, vertical: 16),
                    child: Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'App Volume Lock',
                                style: theme.textTheme.titleMedium?.copyWith(
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                enabled
                                    ? 'Active — media volume is restored when '
                                        'you return to a tracked app.'
                                    : 'Remembers media volume per app and '
                                        'restores it on each return.',
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                            ],
                          ),
                        ),
                        Switch(
                          value: enabled,
                          onChanged: (v) => _toggleAppVolumeLock(state, v),
                        ),
                      ],
                    ),
                  ),
                ),
                if (!_accessibilityGranted)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Card(
                      color: theme.colorScheme.errorContainer,
                      child: InkWell(
                        onTap: () async {
                          await VolumeService.openAccessibilitySettings();
                          await _checkAccessibility();
                        },
                        borderRadius: BorderRadius.circular(16),
                        child: Padding(
                          padding: const EdgeInsets.all(12),
                          child: Row(
                            children: [
                              Icon(Icons.warning_amber_rounded,
                                  color: theme.colorScheme.error),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Text(
                                  'Accessibility Service not enabled. '
                                  'Tap to open settings.',
                                  style: theme.textTheme.bodySmall?.copyWith(
                                    color: theme.colorScheme.onErrorContainer,
                                  ),
                                ),
                              ),
                              Icon(Icons.chevron_right,
                                  color: theme.colorScheme.onErrorContainer),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        'Select apps to track',
                        style: theme.textTheme.titleSmall?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    TextButton.icon(
                      onPressed: _loading ? null : _loadApps,
                      icon: _loading
                          ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.refresh),
                      label: const Text('Refresh'),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                TextField(
                  decoration: InputDecoration(
                    hintText: 'Search apps…',
                    prefixIcon: const Icon(Icons.search),
                    isDense: true,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                  onChanged: (v) => setState(() => _search = v),
                ),
                const SizedBox(height: 8),
              ],
            ),
          ),

          // App list
          Expanded(
            child: apps.isEmpty
                ? Center(
                    child: _loading
                        ? const CircularProgressIndicator()
                        : Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(
                                Icons.apps_outlined,
                                size: 48,
                                color: theme.colorScheme.outline,
                              ),
                              const SizedBox(height: 12),
                              Text(
                                'No apps loaded.\nTap Refresh to load installed apps.',
                                textAlign: TextAlign.center,
                                style: theme.textTheme.bodyMedium?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                            ],
                          ),
                  )
                : ListView.builder(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                    itemCount: apps.length,
                    itemBuilder: (ctx, i) {
                      final app = apps[i];
                      return _AppTile(
                        entry: app,
                        onToggle: (v) =>
                            state.toggleAppTracking(app.packageName, v),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}

// ─── App list tile ────────────────────────────────────────────────────────────

class _AppTile extends StatelessWidget {
  final AppVolumeEntry entry;
  final ValueChanged<bool> onToggle;

  const _AppTile({required this.entry, required this.onToggle});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final vol = entry.rememberedMediaVolume;

    return Card(
      margin: const EdgeInsets.symmetric(vertical: 4),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: entry.isTracked
              ? theme.colorScheme.primaryContainer
              : theme.colorScheme.surfaceContainerHighest,
          child: Text(
            entry.appName.isNotEmpty ? entry.appName[0].toUpperCase() : '?',
            style: TextStyle(
              color: entry.isTracked
                  ? theme.colorScheme.onPrimaryContainer
                  : theme.colorScheme.onSurfaceVariant,
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
        title: Text(
          entry.appName,
          style: theme.textTheme.bodyMedium
              ?.copyWith(fontWeight: FontWeight.w500),
        ),
        subtitle: Text(
          vol != null
              ? 'Remembered volume: $vol'
              : 'No remembered volume yet',
          style: theme.textTheme.bodySmall?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        trailing: Checkbox(
          value: entry.isTracked,
          onChanged: (v) => onToggle(v ?? false),
        ),
        onTap: () => onToggle(!entry.isTracked),
      ),
    );
  }
}
