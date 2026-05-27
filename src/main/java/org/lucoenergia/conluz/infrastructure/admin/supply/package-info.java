/**
 * Supply filtering convention: all user-facing queries must use SupplySpecifications.visibleToCommunities().
 * Cross-community queries (batch jobs, migrations) must use SupplySpecifications.acrossAllCommunities()
 * with a justification comment at the call site.
 */
package org.lucoenergia.conluz.infrastructure.admin.supply;
