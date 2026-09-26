import { QueryClient,QueryClientProvider } from '@tanstack/react-query';
import { render,screen,waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter,RouterProvider,useLocation } from 'react-router-dom';
import { beforeEach,describe,expect,it,vi } from 'vitest';
import { getCollectionCases } from '../../entities/collection/api/collection.api';
import { ApiError } from '../../shared/api/http-client';
import { CollectionsPage } from './CollectionsPage';

vi.mock('../../entities/collection/api/collection.api',()=>({getCollectionCases:vi.fn()}));
const row={id:'11111111-1111-4111-8111-111111111111',customerId:'22222222-2222-4222-8222-222222222222',customerDisplayName:'Acme',invoiceId:'33333333-3333-4333-8333-333333333333',invoiceNumber:'INV-1',status:'OPEN' as const,priority:'HIGH' as const,assignedTo:null,assigneeDisplayName:null,currency:'KZT',outstandingAmount:'1000.00',paymentStatus:'OPEN',nextActionType:'CALL',nextActionDueAt:'2026-09-30T10:00:00Z',nextActionOverdue:false,openedAt:'2026-09-20T10:00:00Z',closedAt:null,closeReason:null,version:0};
const page={items:[row],page:0,size:50,totalElements:1,totalPages:1,hasNext:false};
function Probe(){const l=useLocation();return <output data-testid="location">{l.pathname}{l.search}</output>}
function renderPage(entry='/collections'){const q=new QueryClient({defaultOptions:{queries:{retry:false}}});const router=createMemoryRouter([{path:'/collections',element:<><CollectionsPage/><Probe/></>},{path:'/forbidden',element:<><div>Forbidden</div><Probe/></>}],{initialEntries:[entry]});return render(<QueryClientProvider client={q}><RouterProvider router={router}/></QueryClientProvider>)}
beforeEach(()=>vi.mocked(getCollectionCases).mockResolvedValue(page));
describe('CollectionsPage',()=>{
 it('renders queue projection and canonical deep links',async()=>{renderPage();expect(await screen.findByText('Acme')).toBeInTheDocument();expect(screen.getByRole('link',{name:'Acme'})).toHaveAttribute('href',`/customers/${row.customerId}`);expect(screen.getByRole('link',{name:'INV-1'})).toHaveAttribute('href',`/receivables/invoices/${row.invoiceId}`);expect(screen.getByRole('link',{name:'OPEN'})).toHaveAttribute('href',`/collections/${row.id}`)});
 it('serializes queue filters to URL and server query',async()=>{const u=userEvent.setup();renderPage();await screen.findByText('Acme');await u.selectOptions(screen.getByLabelText('Статус'),'IN_PROGRESS');await waitFor(()=>expect(screen.getByTestId('location')).toHaveTextContent('/collections?status=IN_PROGRESS&page=0'));await waitFor(()=>expect(getCollectionCases).toHaveBeenLastCalledWith(expect.objectContaining({status:'IN_PROGRESS',page:0})))});
 it('forwards customer deep-link scope to the server query',async()=>{renderPage(`/collections?customerId=${row.customerId}`);await screen.findByText('Acme');expect(getCollectionCases).toHaveBeenCalledWith(expect.objectContaining({customerId:row.customerId}))});
 it('routes authorization failures to forbidden',async()=>{vi.mocked(getCollectionCases).mockRejectedValueOnce(new ApiError(403,{status:403}));renderPage();await waitFor(()=>expect(screen.getByTestId('location')).toHaveTextContent('/forbidden'))});
});
