-- ====================================================================
-- TONTINEPAY - SUPABASE POSTGRESQL SCHEMA WITH ROW LEVEL SECURITY (RLS)
-- Autonomous & Transparent ROSCA / Tontine Management System
-- Replaces informal WhatsApp groups with AI-powered payment tracking
-- ====================================================================

-- 1. EXTENSIONS
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. ENUMS
CREATE TYPE tontine_frequency AS ENUM ('weekly', 'monthly', 'biweekly');
CREATE TYPE member_role AS ENUM ('admin', 'treasurer', 'member');
CREATE TYPE cycle_status AS ENUM ('active', 'completed', 'upcoming');
CREATE TYPE transaction_status AS ENUM ('pending_ai', 'pending_validation', 'validated', 'rejected');

-- 3. USERS TABLE
CREATE TABLE IF NOT EXISTS public.users (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    phone_number VARCHAR(30) UNIQUE NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    avatar_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

-- 4. TONTINES TABLE
CREATE TABLE IF NOT EXISTS public.tontines (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(150) NOT NULL,
    description TEXT,
    creator_id UUID NOT NULL REFERENCES public.users(id) ON DELETE RESTRICT,
    contribution_amount NUMERIC(15, 2) NOT NULL CHECK (contribution_amount > 0),
    frequency tontine_frequency DEFAULT 'monthly' NOT NULL,
    currency VARCHAR(10) DEFAULT 'XAF' NOT NULL,
    invite_code VARCHAR(20) UNIQUE,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

-- 5. TONTINE MEMBERS TABLE
CREATE TABLE IF NOT EXISTS public.tontine_members (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tontine_id UUID NOT NULL REFERENCES public.tontines(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    role member_role DEFAULT 'member' NOT NULL,
    payout_order INT NOT NULL CHECK (payout_order > 0),
    joined_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    UNIQUE(tontine_id, user_id),
    UNIQUE(tontine_id, payout_order)
);

-- 6. TONTINE CYCLES TABLE (Rounds / Tours)
CREATE TABLE IF NOT EXISTS public.tontine_cycles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tontine_id UUID NOT NULL REFERENCES public.tontines(id) ON DELETE CASCADE,
    cycle_number INT NOT NULL CHECK (cycle_number > 0),
    beneficiary_id UUID NOT NULL REFERENCES public.users(id) ON DELETE RESTRICT,
    due_date DATE NOT NULL,
    status cycle_status DEFAULT 'active' NOT NULL,
    pot_collected NUMERIC(15, 2) DEFAULT 0 NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    UNIQUE(tontine_id, cycle_number)
);

-- 7. TRANSACTIONS TABLE (Payment Proofs & Validations)
CREATE TABLE IF NOT EXISTS public.transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cycle_id UUID NOT NULL REFERENCES public.tontine_cycles(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES public.users(id) ON DELETE RESTRICT,
    receiver_id UUID NOT NULL REFERENCES public.users(id) ON DELETE RESTRICT,
    amount NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    proof_image_url TEXT,
    extracted_data JSONB DEFAULT '{}'::jsonb NOT NULL,
    status transaction_status DEFAULT 'pending_ai' NOT NULL,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    validated_at TIMESTAMPTZ
);

-- 8. INDEXES & ANTI-DUPLICATE CONSTRAINT (Gemini Vision OCR Anti-Doublon)
CREATE INDEX IF NOT EXISTS idx_transactions_cycle ON public.transactions(cycle_id);
CREATE INDEX IF NOT EXISTS idx_transactions_sender ON public.transactions(sender_id);
CREATE INDEX IF NOT EXISTS idx_transactions_receiver ON public.transactions(receiver_id);
CREATE INDEX IF NOT EXISTS idx_tontine_members_tontine ON public.tontine_members(tontine_id);

-- Enforce anti-duplicate Mobile Money transaction IDs extracted by Gemini:
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_mobile_money_txn 
ON public.transactions ((extracted_data->>'transaction_id'))
WHERE extracted_data->>'transaction_id' IS NOT NULL AND status IN ('pending_validation', 'validated');

-- 9. ENABLE ROW LEVEL SECURITY (RLS)
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tontines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tontine_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tontine_cycles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transactions ENABLE ROW LEVEL SECURITY;

-- 10. RLS POLICIES

-- USERS POLICIES
CREATE POLICY "Users can view members of their tontines" 
ON public.users FOR SELECT USING (
    id = auth.uid() OR
    id IN (
        SELECT tm.user_id FROM public.tontine_members tm
        WHERE tm.tontine_id IN (
            SELECT my_tm.tontine_id FROM public.tontine_members my_tm WHERE my_tm.user_id = auth.uid()
        )
    )
);

CREATE POLICY "Users can update their own profile" 
ON public.users FOR UPDATE USING (id = auth.uid());

CREATE POLICY "Users can insert their own profile" 
ON public.users FOR INSERT WITH CHECK (id = auth.uid());

-- TONTINES POLICIES
CREATE POLICY "Users can view tontines they belong to" 
ON public.tontines FOR SELECT USING (
    id IN (SELECT tm.tontine_id FROM public.tontine_members tm WHERE tm.user_id = auth.uid())
    OR creator_id = auth.uid()
);

CREATE POLICY "Any authenticated user can create a tontine" 
ON public.tontines FOR INSERT WITH CHECK (creator_id = auth.uid());

CREATE POLICY "Admins can update their tontines" 
ON public.tontines FOR UPDATE USING (
    id IN (SELECT tm.tontine_id FROM public.tontine_members tm WHERE tm.user_id = auth.uid() AND tm.role = 'admin')
);

-- TONTINE MEMBERS POLICIES
CREATE POLICY "Members can view co-members in their tontines" 
ON public.tontine_members FOR SELECT USING (
    tontine_id IN (SELECT tm.tontine_id FROM public.tontine_members tm WHERE tm.user_id = auth.uid())
);

CREATE POLICY "Tontine admins can add or manage members" 
ON public.tontine_members FOR ALL USING (
    tontine_id IN (SELECT tm.tontine_id FROM public.tontine_members tm WHERE tm.user_id = auth.uid() AND tm.role = 'admin')
    OR (user_id = auth.uid() AND role = 'admin') -- Creator self-joining as admin
);

-- TONTINE CYCLES POLICIES
CREATE POLICY "Members can view cycles of their tontine" 
ON public.tontine_cycles FOR SELECT USING (
    tontine_id IN (SELECT tm.tontine_id FROM public.tontine_members tm WHERE tm.user_id = auth.uid())
);

CREATE POLICY "Admins can create or modify cycles" 
ON public.tontine_cycles FOR ALL USING (
    tontine_id IN (SELECT tm.tontine_id FROM public.tontine_members tm WHERE tm.user_id = auth.uid() AND tm.role = 'admin')
);

-- TRANSACTIONS POLICIES
CREATE POLICY "Members can view transactions in their tontine cycles" 
ON public.transactions FOR SELECT USING (
    cycle_id IN (
        SELECT tc.id FROM public.tontine_cycles tc
        JOIN public.tontine_members tm ON tm.tontine_id = tc.tontine_id
        WHERE tm.user_id = auth.uid()
    )
);

CREATE POLICY "Members can submit payment proofs for themselves" 
ON public.transactions FOR INSERT WITH CHECK (
    sender_id = auth.uid() AND
    cycle_id IN (
        SELECT tc.id FROM public.tontine_cycles tc
        JOIN public.tontine_members tm ON tm.tontine_id = tc.tontine_id
        WHERE tm.user_id = auth.uid() AND tc.status = 'active'
    )
);

CREATE POLICY "Receivers and Admins can validate or reject transactions" 
ON public.transactions FOR UPDATE USING (
    receiver_id = auth.uid() OR
    cycle_id IN (
        SELECT tc.id FROM public.tontine_cycles tc
        JOIN public.tontine_members tm ON tm.tontine_id = tc.tontine_id
        WHERE tm.user_id = auth.uid() AND tm.role IN ('admin', 'treasurer')
    )
);

-- 11. SUPABASE REALTIME CONFIGURATION
ALTER PUBLICATION supabase_realtime ADD TABLE public.transactions;
ALTER PUBLICATION supabase_realtime ADD TABLE public.tontine_cycles;
ALTER PUBLICATION supabase_realtime ADD TABLE public.tontine_members;

-- 12. HELPER FUNCTION & TRIGGER: Auto-add creator as Admin upon creation
CREATE OR REPLACE FUNCTION public.handle_new_tontine()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.tontine_members (tontine_id, user_id, role, payout_order)
    VALUES (NEW.id, NEW.creator_id, 'admin', 1);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE TRIGGER on_tontine_created
    AFTER INSERT ON public.tontines
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_tontine();
