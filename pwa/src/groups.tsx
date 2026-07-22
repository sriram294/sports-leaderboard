import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from './data';
import type { Group } from './models';

/**
 * Active-group state — the web analog of Android's `GroupRepository.selectedGroup`.
 * The group list is a TanStack Query (so mutations can invalidate it); the active
 * selection is persisted in localStorage and resolves against the loaded list,
 * falling back to the first group if the stored one is gone.
 */
const STORAGE_KEY = 'playboard.group';

type GroupValue = {
  groups: Group[];
  activeGroup?: Group;
  isLoading: boolean;
  error: unknown;
  setActiveGroup: (id: string) => void;
};

const GroupContext = createContext<GroupValue | null>(null);

export function GroupProvider({ children }: { children: ReactNode }) {
  const { data, isLoading, error } = useQuery({ queryKey: ['groups'], queryFn: () => api.groups() });
  const groups = data?.groups ?? [];
  const [selectedId, setSelectedId] = useState<string | null>(() => localStorage.getItem(STORAGE_KEY));

  const activeGroup = useMemo(
    () => groups.find(group => group.id === selectedId) ?? groups[0],
    [groups, selectedId],
  );

  // Keep the persisted id aligned with the resolved active group (covers first
  // load and a stored group that was since removed).
  useEffect(() => {
    if (activeGroup && activeGroup.id !== selectedId) {
      setSelectedId(activeGroup.id);
      localStorage.setItem(STORAGE_KEY, activeGroup.id);
    }
  }, [activeGroup, selectedId]);

  const setActiveGroup = (id: string) => {
    setSelectedId(id);
    localStorage.setItem(STORAGE_KEY, id);
  };

  return (
    <GroupContext.Provider value={{ groups, activeGroup, isLoading, error, setActiveGroup }}>
      {children}
    </GroupContext.Provider>
  );
}

export function useGroups(): GroupValue {
  const value = useContext(GroupContext);
  if (!value) throw new Error('useGroups must be used within GroupProvider');
  return value;
}
